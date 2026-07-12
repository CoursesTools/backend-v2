# Log Playbook

First move on ANY reported issue: read the actual logs. Quote the
real error string back when you explain a fix.

## Where logs live

| Source | Command | Notes |
|---|---|---|
| Backend app (prod) | `ssh ct-backend "docker logs backend --since 1h"` | Container `backend` on VPS 5.129.216.95 (`.env:50`). JSON lines on stdout (prod Spring profile is the default, `application.yml:1-3`). Container clock is UTC even though the host is Europe/Moscow. |
| Loki / Grafana | Grafana on ct-logs VPS 77.232.135.132 port 3000 (`.env:54`); Loki :3100, Prometheus :9090 | Backend ships JSON logs to loki.winworldteam.com via the Docker Loki logging driver configured in the prod compose on the server (`/root/coursestools/docker-compose.yml`) — not in the repo's dev `docker-compose.yml`. Query in Grafana Explore, e.g. `{container_name="backend"} |= "TV activation"` or `{container_name="backend"} | json | level="ERROR"`. |
| Postgres (prod) | `ssh ct-backend "docker logs postgres --since 1h"` | Same host, same compose project. |
| Local dev | console | `dev` profile uses a colored plain-text pattern, not JSON (`src/main/resources/logback-spring.xml:2-11`). |

SSH aliases, passwords, and container layout: see `servers.md` in this folder.

## JSON log format (prod)

Defined in `src/main/resources/logback-spring.xml:12-36`
(logstash-logback `LoggingEventCompositeJsonEncoder`). One JSON object per line:

| Field | Content |
|---|---|
| `@timestamp` | ISO-8601, container-local = UTC |
| `level` | `INFO` / `WARN` / `ERROR` (root level is INFO — no DEBUG in prod, `logback-spring.xml:40`) |
| `logger_name` | Fully-qualified class name (Lombok `@Slf4j`), e.g. `com.winworld.coursestools.service.external.ActivatingSubscriptionService` |
| `message` | The formatted message |
| `thread` | e.g. `http-nio-8080-exec-3`, `AppAsync-1` (async listeners — prefix set in `config/AsyncConfig.java:21`), `scheduling-1` (cron jobs) |
| `stack_trace` | Only on logged exceptions. Shortened: max 5 frames per throwable, root cause FIRST, class names truncated to 30 chars, inline hash (`logback-spring.xml:24-32`) |

MDC is included in the encoder but the codebase never writes to MDC
(no `MDC.` calls in `src/`), so there is **no request id** — correlate by
`userId` / `orderId` / subscription id embedded in messages instead.

## Common log patterns + what they mean

Grep the `message` text; the source line is where to read next.

| Pattern | Meaning | Next step |
|---|---|---|
| `TV activation succeeded` | TV bot `/open` accepted the grant (`service/external/ActivatingSubscriptionService.java:48`) | Access should exist; if user still lacks access, suspect the bot side. |
| `TV activation rejected by bot` / `TV rename rejected by bot` | Bot returned a non-404 error; log includes HTTP status + response body (`ActivatingSubscriptionService.java:43,63`) | Read the body. Rethrown into resilience4j `@Retry`, so expect follow-up lines. |
| `TV activation failed after retries` / `TV rename failed after retries` | resilience4j retries exhausted; durable retry enqueued, caller's transaction still commits (`ActivatingSubscriptionService.java:77,84`) | Watch for `TV retry ... succeeded` from the scheduler (runs every minute, `configs/scheduler.yml:7`). |
| `TV activation failed permanently (nickname not found)` | Bot 404 = nickname doesn't exist on TradingView. Subscription is still set GRANTED and a DEAD retry row is created for admin (`listener/SubscriptionChangeStatusListener.java:71-77`) | Operator must fix the nickname via the admin TV retry page. |
| `TV retry DEAD-enqueued` / `TV retry job moved to DEAD after` | Retry queue gave up (`service/external/TradingViewRetryService.java:135,207`) | Admin action required (force-retry endpoint). |
| `TV retry ACTIVATE succeeded` / `TV retry RENAME succeeded` | Durable retry drained OK (`TradingViewRetryService.java:190,196`) | Incident self-healed. |
| `Error while checking TradingView name` | Signup-time nickname verification against tradingview.com/u/{name} failed (`service/external/TradingViewService.java:35`) | Distinguish TV being down vs. genuinely bad nickname. |
| `Processing Stripe webhook: <type>` | Entry point of every Stripe event (`service/payment/impl/StripePaymentService.java:228`) | Follow subsequent lines from the same thread. |
| `Stripe subscription <id> synced to local subscription <id>` | Lifecycle sync applied (`service/SubscriptionService.java:232`) | — |
| `Stripe subscription update ignored` / `delete ignored` | Webhook arrived for a subscription that is unknown locally or no longer Stripe-backed (`SubscriptionService.java:216-247`) | Usually benign (lifetime/admin grant replaced Stripe); verify DB state. |
| `Processing CryptoCloud postback` | Crypto payment callback received (`service/payment/impl/CryptoPaymentService.java:95`) | JWT-validation errors follow in the same class if the postback is rejected. |
| `Deactivating expired subscriptions job start` (+ trial / grace-period variants) | Hourly subscription cron ticked (`scheduler/SubscriptionScheduler.java:21-37`, cron `configs/scheduler.yml:2-5`) | — |
| `Past-grace reconciliation via <trigger> ...` | Startup / scheduled cleanup of stale subscriptions (`service/SubscriptionStateReconciliationService.java:65-99`) | WARN with a count > 0 means the cron had been failing. |
| `Internal server error` returned to client | `GlobalExceptionHandler` caught an unhandled exception; it logs the real one at ERROR (`exception/GlobalExceptionHandler.java:198-204`) | Grep logger `GlobalExceptionHandler` around that time for the stack trace. |
| `Unexpected exception occurred invoking async method` | Swallowed async listener failure — see next section | Read the attached stack trace; the HTTP request that triggered it already returned 200. |

## The async swallow pattern (read this before trusting a 200)

`config/AsyncConfig.java:13-25` defines only a `ThreadPoolTaskExecutor`; it does
NOT implement `AsyncConfigurer`, so Spring's default
`SimpleAsyncUncaughtExceptionHandler` handles any exception escaping a `void
@Async` method. That handler just logs
`Unexpected exception occurred invoking async method: ...` at ERROR under logger
`org.springframework.aop.interceptor.SimpleAsyncUncaughtExceptionHandler` — the
exception never reaches the caller.

All event listeners in `listener/` are `@Async` (most also
`@TransactionalEventListener`, which fires AFTER_COMMIT), e.g. TV access
activation in `SubscriptionChangeStatusListener.java:50-53`. Consequences:

- A payment webhook can return 200 while TV activation, emails, or alert-bot
  pushes silently failed. Always grep the async handler logger + thread prefix
  `AppAsync-` when "payment worked but access/email didn't".
- If the process dies between commit and the async run, the listener work is
  lost entirely — that is why TV activation has the durable retry queue.

## Filtering tricks

```sh
# Recent errors (raw grep — JSON is one object per line):
ssh ct-backend "docker logs backend --since 2h 2>&1 | grep '\"level\":\"ERROR\"'"

# Pretty errors with jq (fromjson? skips non-JSON lines like the startup banner):
ssh ct-backend "docker logs backend --since 2h 2>&1" \
  | jq -rR 'fromjson? | select(.level=="ERROR")
            | .["@timestamp"] + " " + .logger_name + " " + .message'

# One user's trail (no request id — messages embed userId=... / user {}):
ssh ct-backend "docker logs backend --since 6h 2>&1 | grep -E 'userId=1234|[Uu]ser 1234'"

# Absolute time window — use UTC, the container runs UTC:
ssh ct-backend "docker logs backend --since 2026-07-12T18:00:00Z --until 2026-07-12T19:00:00Z 2>&1"

# Swallowed async failures:
ssh ct-backend "docker logs backend --since 1d 2>&1 | grep 'Unexpected exception occurred invoking async'"
```

In Grafana (ct-logs :3000) use Explore with LogQL, e.g.
`{container_name="backend"} | json | level="ERROR" | line_format "{{.logger_name}} {{.message}}"`.
Loki keeps history past Docker's local rotation, so prefer it for anything
older than the current container.

## Before reaching for a code fix

- **UI bug but the code looks correct** → ask the operator to
  hard-reload first. Cached JS/CSS (CDN styles, stale bundles) often
  shows pre-fix state.
- **Symptom matches something hit before** → check
  `../reference/gotchas.md` first; several classes of bug point away
  from their real cause.
- **"Payment succeeded but no TV access / no email"** → this is almost
  always the async swallow pattern or the TV retry queue, not the payment
  code. Check those loggers before touching Stripe handlers.
- **Can't reproduce** → get more signal (more log lines, DB rows,
  request traces) before guessing. Root-cause or say explicitly that
  you couldn't.
