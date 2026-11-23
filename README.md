# IMDTravel — Tolerância a Falhas (DIMAp/UFRN)

Projeto da disciplina de Tolerância a Falhas do DIMAp/UFRN: um sistema didático de venda
de passagens aéreas, construído para ser derrubado de propósito e medido.

## O sistema

Um cliente pede uma compra ao **IMDTravel**, que orquestra quatro chamadas síncronas, em
ordem obrigatória, a três serviços de dependência:

```
cliente ──POST /buyTicket──▶ IMDTravel ──1. GET  /flight ──▶ AirlinesHub   preço e assentos
                                │      ──2. GET  /convert──▶ Exchange      taxa de câmbio
                                │      ──3. POST /sell   ──▶ AirlinesHub   consome o assento
                                │      ──4. POST /bonus  ──▶ Fidelity      credita os pontos
                                ▼
                     registro local de vendas (GET /sales)
```

| Serviço | Papel | Onde fica / Imagem |
|---|---|---|
| **IMDTravel** | Orquestrador: recebe a compra, faz as 4 chamadas e registra a venda. | [`./imdtravel/`](imdtravel/) (construído localmente via Dockerfile) |
| **AirlinesHub** | Mantém o estoque de assentos por voo e dia. | Imagem oficial: `gibeonufrn/imdtravel-airlineshub:baseline` |
| **Exchange** | Fornece a taxa de câmbio, variando devagar ao longo do tempo. | Imagem oficial: `gibeonufrn/imdtravel-exchange:baseline` |
| **Fidelity** | Acumula pontos de fidelidade por usuário. | Imagem oficial: `gibeonufrn/imdtravel-fidelity:baseline` |

Cada serviço roda no seu próprio container, e todos sobem juntos com Docker Compose na rede interna `imdtravel-net`. Apenas o **IMDTravel** expõe a porta `8080` ao hospedeiro.

---

## Parte 1: Linha de Base (`BASELINE`)

O projeto é dividido em duas etapas. A primeira etapa corresponde à **Linha de Base (Baseline)** do sistema: uma versão funcional, instrumentada para ser observada e **sem nenhum mecanismo de tolerância a falhas**.

A ausência de proteção nesta fase é proposital e constitui o requisito:
- Sem timeouts configurados nas chamadas de saída (usa o padrão do cliente HTTP/sistema operacional).
- Sem repetição (retry) de chamadas que falharam.
- Sem caminhos alternativos, fallbacks, filas ou caches.
- Falhas de dependências resultam em **HTTP 500** imediatamente.

Esta versão servirá como base de comparação para a **Parte 2**, onde as estratégias e padrões de tolerância a falhas serão efetivamente projetados, implementados e medidos. A entrega desta etapa é registrada no repositório através da tag Git **`BASELINE`**.

---

## Implementação da Equipe (`imdtravel`)

O serviço **IMDTravel** foi desenvolvido pela equipe em **Java 21** com **Spring Boot 4**:
- **Orquestração Síncrona:** Realizada pelo `BuyTicketService` consumindo os clientes HTTP (`AirlinesClient`, `ExchangeClient`, `FidelityClient`) via `RestClient` do Spring.
- **Rastreabilidade e Logs Estruturados:** Filtro Servlet intercepta requisições gerando/propagando `X-Correlation-Id` e imprimindo logs em JSON puro no `stdout` (`requisicao.recebida` e `resposta.enviada` com `ts`, `svc`, `cid`, `event`, `ms`, `path` e `status`).
- **Registro Local de Vendas:** Gravado em arquivo `sales.jsonl` dentro do volume nomeado `sales-data` (`/data`), thread-safe com `ReentrantReadWriteLock` e garantia de sincronização em disco (`StandardOpenOption.SYNC`).
- **Verificação de Saúde (`GET /health`):** Retorna `{"status":"ok","uptimeS":...}` validando ativamente a escrita no diretório do volume (`/data/.health`).

---

## Como executar

### 1. Subir o sistema completo (construindo a imagem local do IMDTravel)
```bash
docker compose up -d --build --wait
```
Para verificar o status dos containers:
```bash
docker compose ps
curl http://localhost:8080/health
```

### 2. Parar o sistema
- Para parar mantendo os dados de vendas gravados no volume:
  ```bash
  docker compose down
  ```
- Para parar apagando o volume e zerando as vendas ("começar do zero"):
  ```bash
  docker compose down -v
  ```

---

## Como testar

Para rodar a verificação funcional completa automatizada (incluindo concorrência e dependência fora do ar):

```bash
./testes.sh --reiniciar --com-falhas
```

Para rodar os testes unitários do serviço:
```bash
cd imdtravel
./mvnw test
```

Para mais detalhes sobre testes manuais com `curl` e comandos de observabilidade, consulte o [TESTES.md](TESTES.md).

---

## Catálogo de Voos e Configuração

O catálogo padrão do AirlinesHub é composto por 3 voos ao longo de 7 dias consecutivos (`2026-12-01` a `2026-12-07`), com 20 assentos por par (voo, dia):

| Voo | Preço base (2026-12-01) | Passo por dia | Preço final (2026-12-07) |
|---|---|---|---|
| **IMD100** | US$ 350,50 | +US$ 12,25 | US$ 424,00 |
| **IMD200** | US$ 420,50 | +US$ 12,25 | US$ 494,00 |
| **IMD300** | US$ 510,50 | +US$ 12,25 | US$ 584,00 |

Configurações e taxas de câmbio são injetadas via variáveis de ambiente no [`docker-compose.yml`](docker-compose.yml).

---

## Documentação do Projeto

| Documento | Conteúdo |
|---|---|
| [Especificação da Parte 1](docs/parte1-especificacao.md) | Requisitos formais da disciplina (fonte da verdade). |
| [Como executar](EXECUCAO.md) | Guia detalhado de ambiente, Docker e execução. |
| [Como testar](TESTES.md) | Procedimentos de testes manuais, scripts e validação de logs. |
| [Evidências de Testes](tests/evidencias/EVIDENCIAS.md) | Relatório com saídas e prints das execuções de teste e falhas injetadas. |

---

## Equipe

- Francisca Gabrielly Lopes Freire - 20230034464 - [@gabrielly-freire](https://github.com/gabrielly-freire)
- Jadilson Igor de Campos Lopes - 20240034172 - [@Jahomme](https://github.com/Jahomme)
