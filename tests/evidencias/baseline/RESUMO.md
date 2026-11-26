# Evidências da BASELINE sob Falha — Parte 2

Medições realizadas com o **IMDTravel da tag `BASELINE`** (sem nenhum mecanismo de tolerância a falhas) executando contra as imagens `:parte2` das dependências (`airlineshub`, `exchange` e `fidelity`), utilizando o script [`cenario.sh`](../../../cenario.sh) e um teste adicional com container pausado (`docker compose pause`).

Estas saídas servem como **linha de base de comparação** para a versão `FAULT-TOLERANT`.

---

## Tabela Comparativa dos Cenários

| Cenário | Configuração | Status HTTP | Latência observada | Assentos / `/sales` / Bônus | Estrago observado | Propriedades violadas (Parte 2) | Evidência |
|---|---|---|---|---|---|---|---|
| **`normal`** | `FAULT_*=0` (5 compras, 1s) | 5 × `201` | `0.55s` – `1.19s` | `5 / 5 / 5` | Nenhum (tudo consistente) | — (linha de base sadia) | [normal.txt](normal.txt) |
| **`tempo`** | `FAULT_FLIGHT_P=1`, `TTL=3` | 5 × `201` | `1.20s` – `4.52s` | `5 / 5 / 5` | Todas as requisições aguardam o fim da janela no `GET /flight`; com `TTL` maior ou latência somada, estoura `5000ms` | **P1**, **P6**, **P7** (sem fallback/cache nem proteção contra degradação) | [tempo.txt](tempo.txt) |
| **`resposta`** | `FAULT_CONVERT_P=1`, `TTL=0` | 4 × `201`, 1 × `500` | `0.04s` – `1.61s` | `4 / 4 / 4` | **4 vendas gravadas e aceitas com `201` usando taxa inválida** (`rate=-5.5054` → `valueBRL=-2584.79`; `rate=0` → `valueBRL=0.00`); taxa não numérica causou `500` | **P2** (valores inválidos aceitos), **P6** (compra falha em `nao_numerico`) | [resposta.txt](resposta.txt) |
| **`omissao`** | `FAULT_SELL_P=1`, `TTL=5` | 5 × `500` | `1.17s` – `5.43s` | **`5 / 0 / 0`** | **5 assentos consumidos** no AirlinesHub sem venda em `/sales` e sem bônus; primeira compra levou `5.43s` (`> 5000ms`) | **P1**, **P4**, **P5**, **P7** | [omissao.txt](omissao.txt) |
| **`crash`** | `FAULT_BONUS_P=1`, `TTL=10` | 5 × `500` | `0.56s` – `1.31s` | **`5 / 0 / 0`** | **5 assentos consumidos** no AirlinesHub, mas a queda do Fidelity derrubou todas as compras com `500`, sem registro em `/sales` e sem bônus após recuperação | **P4**, **P5** (Fidelity derrubou a compra e bônus se perdeu), **P7** | [crash.txt](crash.txt) |
| **`container-pausado`** | `docker compose pause airlineshub` | Cliente: `000` (timeout `15s`); Servidor após unpause: `201` (`16.85s`) | `> 15.01s` (cliente desistiu) / `16.85s` (servidor) | `1 / 1 / 1` (concluído após cliente desistir!) | **IMDTravel travou indefinidamente** sem timeout; após o `unpause`, executou a compra em segundo plano (`16851ms`) para um cliente que já havia recebido timeout | **P1** (ultrapassou muito `5000ms`), **P4**, **P7** | [container-pausado.txt](container-pausado.txt) |

---

## Principais Conclusões da Linha de Base

1. **Sem timeout (`P1`):** tanto na `omissao` (`5.43s`) quanto com `airlineshub` pausado (`> 15s` no cliente e `16.85s` no servidor), a `BASELINE` viola o prazo máximo de `5000ms`. No caso pausado, a compra ainda foi concluída após o cliente desistir.
2. **Sem validação de resposta (`P2`):** no cenário `resposta`, o `IMDTravel` aceitou taxas negativas (`-5.5054`) e zeradas (`0`), persistindo vendas com `valueBRL` negativo ou `0.00` e respondendo `201`.
3. **Sem tratamento de omissão após passo irreversível (`P3`, `P4`):** no cenário `omissao`, todos os 5 assentos foram vendidos no `AirlinesHub`, mas o `IMDTravel` respondeu `500`, deixando `5` assentos órfãos (sem registro em `/sales` e sem bônus).
4. **Acoplamento forte ao Fidelity (`P5`):** no cenário `crash`, a queda do `Fidelity` após o `POST /sell` fez todas as 5 compras retornarem `500`, perdendo o registro em `/sales` e nunca creditando os bônus quando o `Fidelity` voltou (~10s depois).
5. **Sem cache/fallback nem circuit breaker (`P6`, `P7`):** sob falhas persistentes, o `IMDTravel` continua batendo nas dependências degradadas em todas as requisições e não oferece operação degradada controlada.
