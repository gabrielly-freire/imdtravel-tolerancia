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

## Parte 2: Tolerância a Falhas (`FAULT-TOLERANT`)

Na Parte 2, o `imdtravel` passa a aplicar estratégias de tolerância a falhas mantendo **1 container por serviço** (sem replicação) e sem alterar os serviços de dependência (imagens `:parte2` com injeção de falhas configurável via `FAULT_*`).

### Parâmetros de Resiliência (Variáveis de Ambiente)

Todos os parâmetros possuem valor padrão declarado no [`docker-compose.yml`](docker-compose.yml):

| Variável | Padrão | Justificativa |
|---|---|---|
| `TIMEOUT_FLIGHT_MS` | `1400` | Tolera picos de concorrência (25 requisições simultâneas) e cold start do `AirlinesHub` (`GET /flight`), mas corta rapidamente chamadas presas em falha de Tempo ou container pausado (`TTL >= 3 s`). |
| `TIMEOUT_CONVERT_MS` | `700` | Margem segura para cold start do `Exchange` (`GET /convert`, normalmente `< 150 ms`), falhando rápido se o serviço travar. |
| `TIMEOUT_SELL_MS` | `1600` | Cobre a latência nominal do `POST /sell` (`SELL_MAX_MS=900 ms`) com folga de `700 ms`, interrompendo bem antes da janela de Omissão (`5 s`). |
| `TIMEOUT_BONUS_MS` | `1100` | Acomoda o aquecimento inicial do `Fidelity` (`~980 ms` medidos na primeira chamada em container), evitando falso positivo de timeout na subida. |
| `DEADLINE_TOTAL_MS` | `4500` | Prazo interno global verificado antes de cada chamada externa (`RequestDeadline`), garantindo `500 ms` de margem abaixo do teto obrigatório de `5000 ms` (**P1**). |
| `CB_FAILURE_RATE_THRESHOLD` | `50` | Percentual de falhas na janela deslizante que dispara a abertura do Circuit Breaker (`CLOSED → OPEN`). |
| `CB_SLIDING_WINDOW_SIZE` | `6` | Janela baseada em contagem por endpoint (`flight`, `convert`, `sell`, `bonus`), evitando abertura por jitter pontual em rajadas concorrentes. |
| `CB_MINIMUM_NUMBER_OF_CALLS` | `3` | Número mínimo de chamadas antes de avaliar a taxa de falha: impede que 1–2 timeouts isolados abram o circuito, mas reage rápido sob falha persistente. |
| `CB_WAIT_DURATION_OPEN_MS` | `5000` | Tempo em estado `OPEN` rejeitando requisições localmente (fail-fast) antes de transicionar automaticamente para `HALF_OPEN`. |
| `CB_HALF_OPEN_PERMITTED_CALLS` | `1` | Quantidade de chamadas de sondagem permitidas em `HALF_OPEN` para confirmar recuperação sem sobrecarregar a dependência. |

> **Endpoint `GET /status` (esquema provisório):** retorna `"status": "ok" | "degraded"`, o estado atual e métricas de cada um dos 4 circuit breakers (`flight`, `convert`, `sell`, `bonus`) e a configuração ativa. (Validar com professor)

### Tabela de Rastreabilidade (Parte 2)

| Falha tolerada | Estratégia adotada (padrão do catálogo) | Onde está no código | Teste que demonstra |
|---|---|---|---|
| **P1** (Prazo total `<= 5000 ms`, inclusive com container pausado) | **Timeout por chamada** (`connectTimeout` + `readTimeout` em HTTP/1.1 por endpoint) + **Prazo interno global (`RequestDeadline`)** antes de cada etapa | [`RestClientConfig.java`](imdtravel/src/main/java/br/ufrn/imd/imdtravel/config/RestClientConfig.java), [`ResilienceProperties.java`](imdtravel/src/main/java/br/ufrn/imd/imdtravel/config/ResilienceProperties.java), [`RequestDeadline.java`](imdtravel/src/main/java/br/ufrn/imd/imdtravel/services/RequestDeadline.java), [`BuyTicketService.java`](imdtravel/src/main/java/br/ufrn/imd/imdtravel/services/BuyTicketService.java) | `BuyTicketServiceTest#shouldStopBeforeCallingNextStageWhenDeadlineExceeded`, `ResilienceTest#requestDeadlineTracksElapsedAndThrowsWhenExpired`, `docker compose pause airlineshub` (`~1.4s` → `504`) |
| **P7** (Falha rápida sob falha persistente, proteção contra sobrecarga e volta automática) | **Circuit Breaker isolado por endpoint** (`Resilience4j`, `COUNT_BASED`, ignora `404`/`409`) + **`GET /status`** + logs `cb.transicao` / `cb.rejeitada` | [`ResilienceConfig.java`](imdtravel/src/main/java/br/ufrn/imd/imdtravel/config/ResilienceConfig.java), [`AirlinesHttpClient.java`](imdtravel/src/main/java/br/ufrn/imd/imdtravel/client/AirlinesHttpClient.java), [`ExchangeHttpClient.java`](imdtravel/src/main/java/br/ufrn/imd/imdtravel/client/ExchangeHttpClient.java), [`FidelityHttpClient.java`](imdtravel/src/main/java/br/ufrn/imd/imdtravel/client/FidelityHttpClient.java), [`StatusController.java`](imdtravel/src/main/java/br/ufrn/imd/imdtravel/controllers/StatusController.java), [`ApiExceptionHandler.java`](imdtravel/src/main/java/br/ufrn/imd/imdtravel/config/ApiExceptionHandler.java) | `ResilienceTest#circuitBreakerOpensAtThresholdRejectsFastAndRecoversAutomatically`, `ResilienceTest#businessExceptionsDoNotOpenCircuitBreaker`, `./cenario.sh tempo 1 8 6` (`503` em `~11–14 ms` após abrir e volta automática em `6 s`) |

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
