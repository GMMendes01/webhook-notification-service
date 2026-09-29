# Webhook Notification Service

Serviço back-end que recebe **eventos** via API, distribui para **assinantes** (webhooks HTTP + e-mail),
reprocessa falhas com **backoff exponencial**, isola entregas irrecuperáveis em **dead-letter** e expõe
**métricas**.

Construído para exercitar conceitos reais de produção sem cair em over-engineering:
outbox transacional, worker de polling, lock distribuído, idempotência, assinatura HMAC e observabilidade.

**Stack:** Java 17 · Spring Boot 3.2 · Maven · PostgreSQL 16 · Redis 7 · Flyway · Micrometer · Testcontainers

---

## Arquitetura

```
                    ┌──────────────── POST /api/events ────────────────┐
                    │  (transação única — o "outbox")                  │
   cliente ───────► │  1. checa idempotência (Redis + unique no banco) │
                    │  2. INSERT events                                 │
                    │  3. INSERT deliveries PENDING (1 por assinante)   │
                    └──────────────────────┬───────────────────────────┘
                                           │  202 Accepted (sem HTTP na request)
                                           ▼
                            ┌──────────────────────────────┐
                            │  DispatchWorker @Scheduled    │
                            │  lock Redis: SET NX EX        │
                            └──────────────┬───────────────┘
                                           ▼
                    ┌─────────────── poll deliveries vencidas ──────────┐
                    │  status IN (PENDING, FAILED) AND next_attempt <=now│
                    └──────────────────────┬────────────────────────────┘
                                           ▼
                    ┌──────── CAS claim → DELIVERING ───────────────────┐
                    │  UPDATE ... WHERE status='PENDING' AND attempt=n  │
                    │  (0 linhas = outro worker pegou, evita duplicidade)│
                    └──────────────────────┬────────────────────────────┘
                                           ▼
                 ┌───────────── WEBHOOK ─────────────┐   ┌──── EMAIL ────┐
                 │ HMAC-SHA256(payload) → headers    │   │ JavaMailSender│
                 │ RestClient, timeout 3s/5s         │   │ → Mailhog     │
                 └──────────────────┬────────────────┘   └───────┬───────┘
                                    ▼                            ▼
                 ┌─────────── registra resultado ────────────────────┐
                 │ 2xx   → SUCCESS                                    │
                 │ falha → FAILED, next = now + min(base·2^n, cap)   │
                 │ n>=max → DEAD_LETTER  (replay via API)            │
                 └───────────────────────────────────────────────────┘
```

### Por que outbox em vez de um broker?

O evento é gravado no Postgres **antes** de qualquer chamada HTTP, então um crash nunca perde um evento.
Um RabbitMQ/Kafka daria as mesmas garantias de durabilidade mas adicionaria mais uma peça de infraestrutura —
para este escopo, o outbox + worker de polling é mais simples de operar e igualmente honesto.

---

## Subindo o projeto

```bash
docker compose up -d          # postgres:5433 · redis:6380 · mailhog 1025/8025
mvn spring-boot:run           # API em http://localhost:8080
```

Caixa de entrada do Mailhog (e-mails "enviados"): http://localhost:8025

---

## API

| Método | Rota | Descrição |
|---|---|---|
| POST | `/api/events` | Ingest do evento. Retorna `202` + id |
| GET | `/api/events` | Lista paginada |
| GET | `/api/events/{id}` | Evento + suas deliveries |
| POST | `/api/subscriptions` | Cadastra assinante (gera secret HMAC) |
| GET | `/api/subscriptions` | Lista |
| GET | `/api/subscriptions/{id}` | Detalhe |
| PUT | `/api/subscriptions/{id}` | Atualiza |
| DELETE | `/api/subscriptions/{id}` | Remove |
| POST | `/api/subscriptions/{id}/secret` | Rotaciona o secret |
| GET | `/api/deliveries` | Filtros `?status=&eventId=&subscriptionId=&page=&size=` |
| GET | `/api/deliveries/{id}` | Tentativas, erro, latência |
| POST | `/api/deliveries/{id}/replay` | Reenfileira uma entrega `DEAD_LETTER` |
| GET | `/api/metrics/summary` | Agregados (sucesso, p95, dead-letter) |
| GET | `/actuator/health` · `/actuator/metrics` | Health + Micrometer |

### Exemplo

```bash
# 1. Cadastrar um assinante
curl -X POST http://localhost:8080/api/subscriptions \
  -H "Content-Type: application/json" \
  -d '{"name":"minha-loja","webhookUrl":"http://localhost:9090/hook",
       "eventTypes":["order.paid"],"emailNotify":true,"emailAddress":"eu@teste.com"}'

# 2. Ingerir um evento
curl -X POST http://localhost:8080/api/events \
  -H "Content-Type: application/json" \
  -d '{"eventType":"order.paid","payload":{"orderId":42,"amount":99.90},
       "idempotencyKey":"order-42"}'
# -> 202 {"eventId":"...","deliveriesCreated":2,"duplicate":false}

# 3. Acompanhar
curl "http://localhost:8080/api/deliveries?status=DEAD_LETTER"
curl -X POST http://localhost:8080/api/deliveries/<id>/replay
curl http://localhost:8080/api/metrics/summary
```

---

## Verificando a assinatura HMAC (lado do receptor)

Cada requisição leva:

| Header | Valor |
|---|---|
| `X-Webhook-Signature` | `sha256=<hex>` |
| `X-Webhook-Event-Id` | id do evento |
| `X-Webhook-Timestamp` | epoch em segundos |

O conteúdo assinado é `timestamp + "." + body`. O timestamp entra na assinatura para permitir
rejeitar replays fora de uma janela de tolerância.

```python
# Verificação no receptor
import hmac, hashlib

def verify(body: bytes, secret: str, timestamp: str, signature_header: str) -> bool:
    signed = f"{timestamp}.".encode() + body
    expected = "sha256=" + hmac.new(secret.encode(), signed, hashlib.sha256).hexdigest()
    return hmac.compare_digest(expected, signature_header)
```

> Sempre compare com `compare_digest` (tempo constante) — nunca com `==`.

---

## Modelo de dados

- **`subscriptions`** — destino (URL/e-mail), secret HMAC, filtro de tipos de evento (`text[]` vazio = todos), ativo.
- **`events`** — tipo, `payload` em `JSONB`, `idempotency_key` único.
- **`deliveries`** — uma linha por evento × assinante × canal. É também o *ledger de retry*:
  `status`, `attempt_count`, `max_attempts`, `next_attempt_at`, `last_status_code`, `last_error`, `response_ms`.

Estados: `PENDING` → `DELIVERING` → `SUCCESS` | `FAILED` (reagenda) | `DEAD_LETTER` (terminal, replayável).

Migrations Flyway em `src/main/resources/db/migration/`.

---

## Configuração (`application.yml`)

| Chave | Default | Descrição |
|---|---|---|
| `webhook.dispatcher.interval-ms` | 2000 | Intervalo do worker |
| `webhook.dispatcher.batch-size` | 50 | Deliveries por passada |
| `webhook.dispatcher.lock-ttl-seconds` | 30 | TTL do lock distribuído |
| `webhook.dispatcher.connect-timeout-ms` | 3000 | Timeout de conexão do webhook |
| `webhook.dispatcher.read-timeout-ms` | 5000 | Timeout de leitura do webhook |
| `webhook.retry.max-attempts` | 5 | Tentativas antes do dead-letter |
| `webhook.retry.base-delay-seconds` | 5 | Base do backoff exponencial |
| `webhook.retry.max-delay-seconds` | 900 | Teto do backoff |
| `webhook.idempotency.ttl-hours` | 24 | TTL do cache de idempotência |

---

## Testes

```bash
mvn test           # unitários (sem Docker): HMAC, backoff, filtro de assinatura
mvn verify         # + integração (Testcontainers: Postgres + Redis reais, MockWebServer)
```

> `mvn verify` exige o Docker rodando. Testcontainers 2.x fala com Docker Engine 29+
> sem precisar de workaround de API version.

- **`HmacSignerTest`** — vetor conhecido, detecção de payload adulterado, secret errado, replay de timestamp.
- **`RetryBackoffPolicyTest`** — sequência 5s→10s→20s→40s, clamping no teto, exaustão.
- **`IntegrationTestBase`** — sobe a app contra Postgres e Redis em containers.
- **`IngestAndDispatchIT`** — caminho feliz (200 → `SUCCESS` + headers assinados),
  caminho de falha (500×3 → `DEAD_LETTER` → replay → `SUCCESS`), filtro por tipo de evento e idempotência.

---

## Próximos passos

- Prometheus + Grafana sobre os endpoints do Actuator
- Rate limiting no ingest (contador no Redis)
- Autenticação por API key nos endpoints administrativos
- Fencing token no lock (hoje o release não verifica ownership — ver `RedisLockService`)
- Suporte a `PATCH` de assinatura e versionamento de API

---

## Estrutura

```
src/main/java/br/com/portfolio/webhook
├── config/       WebhookProperties, AppConfig (RestClient, Redis, backoff)
├── controller/   Event, Subscription, Delivery, Metrics
├── dto/          requests/responses + DeliveryOutcome, MetricsSummary
├── model/        Event, Subscription, Delivery + enums
├── repository/   JPA + MetricsRepository (SQL nativo com percentile_cont)
├── security/     HmacSigner
├── service/      Ingest, Dispatch, Webhook, Email, Subscription, Metrics, RedisLock, DeliveryQuery
├── worker/       DispatchWorker (@Scheduled)
└── exception/    GlobalExceptionHandler (ProblemDetail / RFC 7807)
```
