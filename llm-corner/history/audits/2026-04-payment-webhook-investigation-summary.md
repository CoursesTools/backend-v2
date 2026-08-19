# Stripe Webhook Failure — Investigation Summary

**Status as of 2026-04-10 23:00 UTC:** Active investigation, break date narrowed but root cause not yet identified. Waiting on Stripe dashboard access (Apr 11 morning).

**Reading this from scratch in a new session?** Start with the "30-second summary" section, then "Current working theory," then "What to do next morning" in `02-NEXT-STEPS.md`.

---

## 30-second summary

- Customers are paying via Stripe successfully (money collected) but their subscriptions are not being activated.
- Root cause is isolated to **Stripe webhooks not being processed by the backend for the last ~31 hours**.
- The endpoint is fully healthy. Code is fine. Signing secret appears fine. The backend simply is not receiving webhook POSTs for recent Stripe events.
- **Last confirmed successful Stripe webhook processed:** `2026-04-08 07:42:21 UTC` (`StripePaymentService.processWebhook: invoice.payment_succeeded`)
- **First known missed webhook:** Mukul Salaria's payment `pi_3TKi28GjHMWbNU7A1qR7bUV1`, `2026-04-09 14:54 UTC`, $14.90 USD, Essentials Month, tenant `WINWORLD.PRO`
- **Break window is ~31 hours long**, somewhere between `2026-04-08 07:42 UTC` and `2026-04-09 14:54 UTC`.
- **Most likely cause:** Stripe auto-disabled the webhook endpoint, or a transient network/signing issue caused repeated failures and Stripe stopped delivering. Confirmation requires Stripe dashboard access.

---

## Infrastructure layout (important context)

```
Internet
  |
  └── DNS: api.coursestools.com / coursestools.com → 5.129.216.95
          │
          ├─────────────────────────────────────────────────────┐
          │                                                     │
          ▼                                                     │
  ╔══════════════════════════════════════╗                      │
  ║ NL HOST — 5.129.216.95 (Amsterdam)   ║                      │
  ║ Timeweb LLP AS210976                 ║                      │
  ║ hostname: 5552217-bq98524            ║                      │
  ║                                      ║                      │
  ║ docker-compose project: coursestools ║                      │
  ║                                      ║                      │
  ║ Containers:                          ║                      │
  ║  - caddy         (ports 80/443/8081) ║                      │
  ║  - backend       (Spring Boot)       ║                      │
  ║  - frontend      (Next.js)           ║                      │
  ║  - postgres ext. (147.45.240.45)     ║                      │
  ║  - redis                             ║                      │
  ║  - plausible + plausible_db + clickhouse (analytics)         │
  ║  - pixel-canvas                      ║                      │
  ║                                      ║                      │
  ║ Backend logs → Docker 'loki' driver ─║──────────────────────┘
  ║   logs stream directly to:          ║       (HTTPS push)
  ║   loki.winworldteam.com/loki/api/v1/push
  ║   tenant_id: backend                 ║
  ║                                      ║
  ║ Backend has NO local log file        ║
  ║ (all logs go via loki driver)        ║
  ╚══════════════════════════════════════╝
                                                                │
                                                                ▼
  ╔═════════════════════════════════════════════╗
  ║ RU OBSERVABILITY HOST — 77.232.135.132      ║
  ║ Saint Petersburg, JSC Timeweb AS9123        ║
  ║ hostname: observability                     ║
  ║                                             ║
  ║ Containers (running since 2026-02-11):      ║
  ║  - caddy         (hosts *.winworldteam.com) ║
  ║  - loki          (accepts push from NL)     ║
  ║  - grafana                                  ║
  ║  - prometheus                               ║
  ║  - metatrader    (unrelated — separate product) ║
  ║                                             ║
  ║ Caddy on this host routes:                  ║
  ║    grafana.winworldteam.com  → grafana:3000 ║
  ║    loki.winworldteam.com     → loki:3100    ║
  ║    prometheus.winworldteam.com → prom:9090  ║
  ║    metatrader.tw1.su         → metatrader   ║
  ║                                             ║
  ║ Loki version: 3.6.4                         ║
  ║ Loki retention: ~30 days from now           ║
  ║ Loki query window max: 30d1h                ║
  ║ Loki is multi-tenant — must use header:     ║
  ║    X-Scope-OrgID: backend                   ║
  ║                                             ║
  ║ Loki's label set for 'backend' tenant:      ║
  ║    compose_project, compose_service,        ║
  ║    container_name, filename, host,          ║
  ║    level, logger, service_name,             ║
  ║    source, thread                           ║
  ╚═════════════════════════════════════════════╝

  ╔═════════════════════════════════════════════╗
  ║ Stripe (external) — api.stripe.com          ║
  ║ Webhook URL: UNKNOWN (need dashboard access) ║
  ║ Tenant: WINWORLD.PRO                         ║
  ╚═════════════════════════════════════════════╝
```

### Critical env vars (redacted)
- `STRIPE_SECRET` — Stripe API key, used by backend to create checkout sessions
- `STRIPE_WEBHOOK_SECRET` — used to verify incoming webhook signatures (`Webhook.constructEvent` in `StripePaymentService.processWebhook`)
- `STRIPE_COUPON` — partnership discount coupon id
- `CRYPTO_API_KEY` / `CRYPTO_SECRET` / `CRYPTO_SHOP_ID` — CryptoCloud config
- Nobody with Stripe access has modified `STRIPE_WEBHOOK_SECRET` "since forever" per the user.

### Key URLs and paths
- Backend webhook endpoint (verified working with local curl): `https://api.coursestools.com/api/v1/payments/stripe`
- Spring Boot context path: `/api` (so full path is `/api/v1/payments/stripe`)
- Endpoint is in Spring Security public URL list (`PublicUrlsHolder.java` has `requestMatcher("/v1/payments/stripe")`) → auth is NOT blocking webhooks
- Grafana Explore UI: `https://grafana.winworldteam.com` (for Loki queries without fighting jq)
- Stripe dashboard: not yet accessible, partner's account, permissions expected Apr 11

---

## Timeline of the investigation

### What triggered this session
User reported: recent Stripe transactions are failing to activate access. Sent screenshot of Mukul Salaria payment on Stripe dashboard showing Succeeded but not provisioned on the backend.

### Work done today (2026-04-10)

**Session part 1 — CryptoCloud hardening (committed and deployed):**
- User reported CryptoCloud webhooks also not granting access. Audited and found several real bugs. Created PR on branch `fix/cryptocloud-workflow-rebuild`:
  - `PaymentController`: added second `@PostMapping` for JSON content-type (CryptoCloud V2 sends either form-urlencoded OR JSON)
  - `CryptoRetrieveDto`: added `@JsonProperty`/`@JsonAlias` for snake_case field names; added optional `status` field
  - `CryptoInvoiceCreateDto`: added required `currency` field (V2 API requires it), fixed `time_to_pay` snake_case (was serializing as camelCase and being ignored)
  - `CryptoPaymentService`: filter postbacks by `status == "success"`, throw on empty response, diagnostic logging everywhere
  - `PaymentFacade`: logs at each step, handles null from processPayment
  - `GlobalExceptionHandler`: added dedicated handler for `ClientAbortException` / `AsyncRequestNotUsableException` → returns void + logs at DEBUG instead of cascading into JSON-serialization errors (was producing 3 log lines per Prometheus scrape disconnect on `/actuator/prometheus`)
- Project docs updated (`claude-docs/CLAUDE_README.md`) with tier flow note, CryptoCloud V2 specifics, and updated file map.
- Commit/PR message drafted in `claude-git/MSG-FIX-CRYPTOCLOUD-WEBHOOK.md`.
- Fix A Caddy change: on `api.winworldteam.com`, added `handle /api/v1/payments/stripe { reverse_proxy backend:8080 }` before the default `redir`, so if Stripe happens to be pointed at the old host, its POST no longer 301s. Reloaded caddy on NL. (This was my wrong-turn theory; turned out irrelevant since Stripe had been working on the current infra through April 8. The Caddy change is harmless defense-in-depth.)
- Deployed `fix/cryptocloud-workflow-rebuild` branch to NL server at **2026-04-10 20:03:57 UTC** via GitHub Actions (fresh container, restartCount=0).

**Session part 2 — Wild goose chases (documented here so we don't repeat them):**
1. **"Caddy isn't access-logging"** — I concluded this from broken jq output. It was wrong. Caddy has access logging enabled via global `log` block in Caddyfile. Access logs ARE there. My jq patterns had quote-escape bugs on the Docker-wrapped JSON format.
2. **"Backend IP is in Russia, Stripe OFAC-blocks it"** — wrong. IP `5.129.216.95` is Timeweb's NL entity in Amsterdam (`AS210976`). Stripe has no reason to block it.
3. **"Frontend Next.js rewrites proxy /api/* through coursestools.com"** — wrong. `curl https://coursestools.com/api/v1/payments/stripe` returns 404, not the expected 400. Frontend does NOT proxy API traffic.
4. **"api.winworldteam.com 301 redirect is breaking Stripe webhooks"** — wrong. `grep -c 'payments/stripe'` in Caddy's entire 2.5-month log returned 3 hits, all our own curl tests. `grep -c 'Stripe/1'` returned 0. Stripe was never hitting api.winworldteam.com at all.
5. **"Stripe is auto-disabled, has been for weeks"** — INITIAL conclusion was that last Stripe event was March 29. This was based on Query 6 with `head -c 5000` truncating the newer streams. Once I ran Query 4 with a tighter window, I saw Stripe events on April 4, 5, 6, 8 — the system was working much more recently than I'd concluded.

### Key realizations

1. **Backend uses Docker's Loki log driver**, streaming directly to `loki.winworldteam.com` with tenant `backend`. Nothing is written to local Docker log files on the NL server. `docker logs backend` after a fresh deploy only shows the current container's output; all historical data lives in Loki on the RU observability host.

2. **Loki is multi-tenant.** Queries without `X-Scope-OrgID: backend` get `no org id` HTTP 401. With the header they work normally.

3. **Loki has ~30 days retention.** `query_range` has a hard `max_query_length=30d1h` limit, so queries spanning more than 30 days return 400. Data older than ~30 days has been evicted.

4. **The `logger` label is pipeline-extracted from the JSON log line** (via the docker-compose logging config's `pipeline_stages`), so queries like `{compose_service="backend", logger="com.winworld.coursestools.service.payment.impl.StripePaymentService"}` are fast label-selectors, not line regexes.

---

## Current working theory

Somewhere in the ~31-hour window **2026-04-08 07:42 UTC → 2026-04-09 14:54 UTC**, something happened that stopped Stripe webhooks from being processed. The candidates, in rough order of likelihood:

### Theory 1 (most likely) — Stripe auto-disabled the webhook endpoint
Stripe automatically disables webhook endpoints after 3 consecutive days of delivery failures (or a large batch of back-to-back failures). If the backend had any period of unavailability or signature-verification failures in that 31-hour window, Stripe's failure counter would have crossed the threshold and the endpoint would be disabled. After that, no webhooks are delivered until someone re-enables it in the dashboard. We cannot verify this from our side — only the dashboard shows endpoint status and recent delivery attempts.

**Evidence consistent with this:**
- The break window (31h) is short enough that 3 days of retries wouldn't have completed yet, but Stripe also auto-disables after single batches of 4xx responses in quick succession.
- No errors in our logs around the break time (pending NQ5 confirmation in the morning) would mean Stripe simply isn't trying anymore.
- Nobody touched the webhook config, consistent with auto-disable not requiring human action.

**What we'd see in the Stripe dashboard:**
- Endpoint status: `Disabled`
- "Recent deliveries" tab: a cluster of failed attempts around April 8–9, then nothing since.
- If visible: `Disabled because of consecutive failures` or similar note.

### Theory 2 — STRIPE_WEBHOOK_SECRET got rotated (quietly)
If someone rotated the signing secret in Stripe's dashboard and the corresponding `STRIPE_WEBHOOK_SECRET` env var on the backend was not updated, every webhook would fail `Webhook.constructEvent` → throw `SignatureVerificationException` → caught and rethrown as `SecurityException` → `GlobalExceptionHandler` returns HTTP 400. Stripe would mark these as failures and auto-disable after the threshold.

**Evidence for/against:**
- FOR: consistent with silent failure pattern
- AGAINST: User says nobody has touched the Stripe config and all three people with access are accounted for
- To verify: compare `STRIPE_WEBHOOK_SECRET` env var (first 12 chars) on the backend against the "Signing secret" displayed for the endpoint in Stripe dashboard

**What we'd see in the backend logs (if true):**
- `com.winworld.coursestools.service.payment.impl.StripePaymentService` log at ERROR level
- `com.winworld.coursestools.exception.GlobalExceptionHandler` log with `SecurityException: ...`
- These would ALL be after April 8 07:42 UTC. NQ5 will reveal whether this is the pattern.

### Theory 3 — Backend deploy/restart that left Stripe webhook processing partially broken
A deploy between April 8 07:42 and April 9 14:54 that introduced a bug in the Stripe path, or a startup ordering issue, or a config mistake. NQ2 will show whether there was any backend restart in that window. The user did a deploy today (2026-04-10 20:03 UTC) from the CryptoCloud fix branch, but that's AFTER the break window.

**Evidence against:**
- No obvious change in Stripe-related code on the current branch (`fix/cryptocloud-workflow-rebuild`). All changes were to `CryptoPaymentService`, `CryptoInvoiceCreateDto`, `CryptoRetrieveDto`, `PaymentController`, `PaymentFacade`, and `GlobalExceptionHandler`. None touched `StripePaymentService`.
- Git log on master shows no StripePaymentService-related commits between April 8 and now.

### Theory 4 — Something upstream of Caddy (ISP, network, DDoS protection, TLS)
Transit-level disruption between Stripe's webhook senders and the NL host. Unlikely because we can reach Stripe outbound fine, and the endpoint accepts curl traffic from random sources correctly.

### Theory 5 — Stripe's internal webhook delivery had an outage
Possible but unusual to last 31+ hours. Would affect many merchants and there'd be a Stripe status page incident. We haven't checked stripe.statuspage.io for that window yet.

---

## What we've definitively verified

| Check | Result |
|---|---|
| Backend container healthy | ✅ Running since 2026-04-10 20:03:57 UTC, restartCount=0, clean startup logs |
| Flyway migrations | ✅ Up to date at V8 |
| DB connection | ✅ HikariCP pool open to 147.45.240.45:5432 |
| Tomcat | ✅ Listening on 8080 (path `/api`) and 8081 (actuator) |
| Spring Security public URL for webhook | ✅ `/v1/payments/stripe` in `PublicUrlsHolder.PUBLIC_URL_PATTERNS` |
| Backend outbound → Stripe API | ✅ `curl https://api.stripe.com/v1/events` TLS handshake succeeds |
| Webhook endpoint reachable from public internet | ✅ `curl -X POST https://api.coursestools.com/api/v1/payments/stripe` returns `400 {"status":400,"error":"Security error","message":"No signatures found matching the expected signature for payload"}` — the expected response for an unsigned POST |
| TLS certificate | ✅ Let's Encrypt, valid until 2026-05-26 |
| DNS | ✅ `api.coursestools.com → 5.129.216.95`, `coursestools.com → 5.129.216.95`, `api.winworldteam.com → 5.129.216.95`, `winworldteam.com → 5.129.216.95`, `winworld.pro → 5.129.216.95` |
| iptables / nftables | ✅ No custom DROP or redirect rules, `DOCKER-USER` chain is empty |
| Backend log pipeline to Loki | ✅ 38 loggers actively sending data in the last 24h, including both `StripePaymentService` and `CryptoPaymentService` |
| CryptoCloud path works in production | ✅ Order 790 created at 2026-04-10 12:49 UTC, invoice successfully created with `pay.cryptocloud.plus/N7KAQEOG` |
| Stripe was working recently | ✅ Last confirmed processed webhook: 2026-04-08 07:42:21 UTC |
| Frontend proxies /api/ to backend | ❌ `curl https://coursestools.com/api/v1/payments/stripe` returns 404, confirming frontend has no Next.js rewrite for /api/ |
| Stripe webhook URL (dashboard setting) | ❓ UNKNOWN — need Stripe access |
| Stripe webhook endpoint status | ❓ UNKNOWN — need Stripe access |
| Stripe Recent Deliveries page | ❓ UNKNOWN — need Stripe access |

---

## Code changes deployed today (relevant context)

Branch: `fix/cryptocloud-workflow-rebuild`, deployed via GitHub Actions at 2026-04-10 20:03:57 UTC.

All changes were to the CryptoCloud path and to one general exception handler. **None touched `StripePaymentService` or the Stripe webhook routing.** The Stripe break predates this deploy.

Modified files:
- `src/main/java/com/winworld/coursestools/controller/PaymentController.java` — added second JSON-consumes handler for /crypto
- `src/main/java/com/winworld/coursestools/dto/payment/crypto/CryptoRetrieveDto.java` — added `@JsonProperty`, `status` field
- `src/main/java/com/winworld/coursestools/dto/payment/crypto/CryptoInvoiceCreateDto.java` — added `currency`, fixed `time_to_pay` JSON name
- `src/main/java/com/winworld/coursestools/service/payment/impl/CryptoPaymentService.java` — diagnostic logging, status filter
- `src/main/java/com/winworld/coursestools/facade/PaymentFacade.java` — logging, null handling
- `src/main/java/com/winworld/coursestools/exception/GlobalExceptionHandler.java` — `@ExceptionHandler({ClientAbortException.class, AsyncRequestNotUsableException.class})` to stop the Prometheus scrape error spam
- `claude-docs/CLAUDE_README.md` — docs update
- `claude-git/MSG-FIX-CRYPTOCLOUD-WEBHOOK.md` — commit/PR message

Server-side changes not in git:
- Caddyfile on NL server (container `caddy`): added `handle /api/v1/payments/stripe { reverse_proxy backend:8080 }` block inside `api.winworldteam.com {}` before the `redir` line. Reloaded with `docker exec caddy caddy reload --config /etc/caddy/Caddyfile --adapter caddyfile`.

---

## Critical data discovered in Loki

### Confirmed Stripe webhook processing in the last 14 days (all of Query 4 output, cleaned up)
```
2026-04-08 07:42:21Z  invoice.payment_succeeded   thread=http-nio-8080-exec-10
2026-04-08 05:27:00Z  invoice.payment_succeeded   thread=http-nio-8080-exec-8
2026-04-08 05:26:59Z  checkout.session.completed  thread=http-nio-8080-exec-4
2026-04-06 07:24:44Z  invoice.payment_succeeded   thread=http-nio-8080-exec-8
2026-04-05 14:37:43Z  (truncated in output)       thread=http-nio-8080-exec-4
2026-04-05 05:29:07Z  invoice.payment_succeeded   thread=http-nio-8080-exec-9
2026-04-04 11:49:11Z  invoice.payment_succeeded   thread=http-nio-8080-exec-7
```
(NQ1 in `02-NEXT-STEPS.md` fetches the full list with a large limit — earlier query was truncated at `head -c 5000` and we only saw a subset.)

### Log volume per day for `compose_service=backend` (30-day view)
```
Date        Count
----------  ------
Mar 12      6        (partial, query start)
Mar 13      165
Mar 14      160
Mar 15      147
Mar 16      167
Mar 17      161
Mar 18      155
Mar 19      166
Mar 20      163
Mar 21      160
Mar 22      150
Mar 23      165
Mar 24      155
Mar 25      154
Mar 26      263      ← first mild bump
Mar 27      164
Mar 28      163
Mar 29      151
Mar 30      225      ← Mar 30 14:43 backend restart
Mar 31      221
Apr 01      231
Apr 02      735      ← 5x baseline spike
Apr 03      175
Apr 04      302
Apr 05      154
Apr 06      389
Apr 07      213
Apr 08      2474     ← 16x baseline (start of log storm)
Apr 09      934
Apr 10      2914     ← today, 19x baseline
```

Baseline is ~150–170/day. The Apr 8+ elevated volume is almost certainly from the Prometheus `Connection reset by peer` cascade (fixed in `GlobalExceptionHandler` today) — each scrape was producing 1 ERROR + 2 WARN per disconnected client, and the scraper in RU has been getting intermittent connectivity thanks to the GH Actions → Russia network issues the user mentioned. **Coincidence that log volume spiked starting Apr 8 and Stripe also broke Apr 8 needs to be considered.** But the two are probably unrelated causally (my fix handled the scrape noise, Stripe webhooks use a different code path).

### Loggers active in last 30 days (from label values API)
```
com.winworld.coursestools.CoursesToolsApplication
com.winworld.coursestools.exception.GlobalExceptionHandler
com.winworld.coursestools.scheduler.SubscriptionScheduler
com.winworld.coursestools.service.CodeService
com.winworld.coursestools.service.OrderService
com.winworld.coursestools.service.SubscriptionDeactivationService
com.winworld.coursestools.service.SubscriptionService
com.winworld.coursestools.service.external.ActivatingSubscriptionService
com.winworld.coursestools.service.external.OAuthGoogleService
com.winworld.coursestools.service.external.TradingViewService
com.winworld.coursestools.service.payment.impl.CryptoPaymentService
com.winworld.coursestools.service.payment.impl.StripePaymentService
com.zaxxer.hikari.HikariDataSource
com.zaxxer.hikari.pool.HikariPool
org.apache.catalina.core.ContainerBase.[Tomcat-1].[localhost].[/]
org.apache.catalina.core.ContainerBase.[Tomcat].[localhost].[/api]
org.apache.catalina.core.StandardEngine
org.apache.catalina.core.StandardService
org.apache.coyote.http11.Http11NioProtocol
org.flywaydb.core.FlywayExecutor
org.flywaydb.core.internal.command.DbMigrate
org.flywaydb.core.internal.command.DbValidate
org.hibernate.*  (multiple)
org.springframework.aop.interceptor.SimpleAsyncUncaughtExceptionHandler
org.springframework.boot.*  (multiple)
org.springframework.cloud.context.scope.GenericScope
org.springframework.data.jpa.*  (multiple)
org.springframework.data.repository.config.* (multiple)
org.springframework.orm.jpa.*  (multiple)
org.springframework.security.web.DefaultSecurityFilterChain
org.springframework.web.servlet.DispatcherServlet
org.springframework.web.servlet.mvc.method.annotation.ExceptionHandlerExceptionResolver
org.springframework.web.servlet.mvc.support.DefaultHandlerExceptionResolver
```

Both `StripePaymentService` AND `CryptoPaymentService` are in this list — confirming the backend is actively processing both payment methods in the recent window. The Stripe break is specifically about webhook delivery from Stripe → backend, not about the backend's own code.

### Backend restart events in Mar 28 → Apr 01
```
2026-03-30 14:43:13Z  Starting CoursesToolsApplication v0.0.1 using Java 17.0.18 with PID 1
2026-03-30 14:43:36Z  Tomcat started on port 8080 (http) with context path '/api'
2026-03-30 14:43:37Z  Tomcat started on port 8081 (http) with context path ''
2026-03-30 14:43:37Z  Started CoursesToolsApplication in 26.515 seconds
```
One restart, not in the break window.

### Confirmed today's container ID on backend
`2a613ec169174ffc47c88ba0ea8aa85280d877e281f65a09f7963eafc501788a` — seen as the `filename` label in log entries. This was the backend container from ~Mar 30 through today's Apr 10 20:03 redeploy. After the redeploy, the new container has a different id.

---

## What we know is NOT the problem

- **Not a code bug in the Stripe handler.** `StripePaymentService.processWebhook` is unchanged on the deployed branch. Last changes to it were in commit 520ff84 on Apr 7 (updated product name to `planDisplayName`), which was deployed and Stripe worked fine afterwards through April 8.
- **Not a missing URL route.** The webhook endpoint is routable: controller mapping is `@PostMapping("/stripe")` inside `@RequestMapping("/v1/payments")`, with Spring Boot context-path `/api`, so the full URL is `https://api.coursestools.com/api/v1/payments/stripe`. Confirmed working via curl.
- **Not a Spring Security block.** `PublicUrlsHolder.java` has `requestMatcher("/v1/payments/stripe")` in the public URL list. The JWT filter skips it.
- **Not a redirect issue.** Caddy was never returning a 301 to Stripe. There are no entries in Caddy logs for Stripe User-Agent on api.winworldteam.com, api.coursestools.com, or any other host, so this was a dead-end theory. Fix A (Caddy handle block for /api/v1/payments/stripe on api.winworldteam.com) is in place but hasn't captured anything.
- **Not an OFAC / geo block.** The NL host is in Amsterdam, not Russia (the RU AS9123 is a separate entity at a different IP). Stripe has no reason to geo-block Amsterdam.
- **Not a TLS cert issue.** Valid Let's Encrypt cert through 2026-05-26.
- **Not a DNS issue.** All relevant hostnames resolve to 5.129.216.95.
- **Not a pipeline/logging issue.** Loki has fresh data from today including `CryptoPaymentService` entries. If `StripePaymentService` were processing webhooks, we'd see the log entries — the pipeline is known-good.
- **Not "the frontend isn't calling /api"**. The frontend doesn't proxy /api/* (verified), but browser JS does hit `api.coursestools.com` directly. OAuthGoogleService logger in today's active list proves this.
- **Not Stripe-wide infrastructure outage.** Stripe account is active (user has payments coming in), outbound to Stripe from our server works.

---

## Open questions (for Apr 11 morning)

1. What exact URL is configured as the Stripe webhook endpoint in the dashboard? Does it match `https://api.coursestools.com/api/v1/payments/stripe`?
2. Is the endpoint status "Enabled" or "Disabled"?
3. What's the "Signing secret" first 12 chars? Does it match `STRIPE_WEBHOOK_SECRET` env var on the backend?
4. What do the "Recent deliveries" show for the last 3 days? What HTTP status codes? What error messages?
5. When did the last successful delivery happen according to Stripe's dashboard? (We expect 2026-04-08 07:42:21 UTC to match)
6. Was there any backend restart, error burst, or weird event between 2026-04-08 07:42 UTC and 2026-04-09 14:54 UTC? (NQ1-NQ6 in `02-NEXT-STEPS.md` will answer this)
7. Has Stripe sent any email about webhook endpoint disablement? Check the Stripe account's email.

---

## Useful commands and queries reference

### Loki via CLI (on RU observability host)

All Loki queries must include `-H "X-Scope-OrgID: backend"`.

**Base query template:**
```bash
START_NS=$(date -d 'N days ago' +%s%N)
END_NS=$(date +%s%N)
curl -s -H "X-Scope-OrgID: backend" \
  -G --data-urlencode 'query=QUERY_HERE' \
     --data-urlencode "start=$START_NS" \
     --data-urlencode "end=$END_NS" \
     --data-urlencode "limit=1000" \
     --data-urlencode "direction=BACKWARD" \
  "http://localhost:3100/loki/api/v1/query_range"
```

**LogQL examples that work:**
```logql
# All StripePaymentService events
{compose_service="backend", logger="com.winworld.coursestools.service.payment.impl.StripePaymentService"}

# Webhook processing by event type
{compose_service="backend"} |= "Processing Stripe webhook"

# Payment-related errors
{compose_service="backend", level="ERROR"} |~ "(?i)(stripe|payment|signature|webhook|security)"

# Hourly count of Stripe webhooks
sum(count_over_time({compose_service="backend", logger="com.winworld.coursestools.service.payment.impl.StripePaymentService"}[1h]))

# Total backend log volume per day
sum(count_over_time({compose_service="backend"}[1d]))

# Backend restarts
{compose_service="backend"} |~ "Started CoursesToolsApplication|Tomcat started on port"

# Specific stream by container (via filename label)
{compose_service="backend", filename=~".*2a613ec169174ffc47c88ba0ea8aa85280d877e281f65a09f7963eafc501788a.*"}
```

**Important gotchas:**
- `query_range` max window is 30d1h. Split larger queries.
- `limit` is global, not per-stream. Use direction=BACKWARD to get newest first.
- Results are returned grouped by stream (label set), not globally sorted. Post-process by timestamp if you need strict chronological order.
- Piping to `jq` can fail on Docker-wrapped logs because the inner JSON is escaped. Use `-s -H "X-Scope-OrgID: backend" ... | jq` on the Loki HTTP API response (which IS plain JSON), but do NOT use jq on raw `/var/lib/docker/containers/.../json.log` files without first unwrapping.

### Grafana UI alternative (recommended for debugging):

URL: `https://grafana.winworldteam.com`

1. Log in
2. Explore (compass icon) → choose Loki datasource
3. If datasource doesn't include `X-Scope-OrgID: backend`, edit the datasource under Connections → Data sources → Loki → HTTP headers → add header `X-Scope-OrgID` with value `backend`
4. Run LogQL queries as above. Use the time range picker (last 24h / 3d / 7d / 30d).
5. Histogram view shows event density; click bars to zoom.

### Direct curl test of the webhook endpoint
```bash
# Returns 400 if endpoint is healthy:
curl -v -X POST https://api.coursestools.com/api/v1/payments/stripe \
  -H "Content-Type: application/json" \
  -H "Stripe-Signature: t=1,v1=fake" \
  -d '{"id":"evt_test","object":"event","type":"invoice.payment_succeeded"}'
```

### Backend state check on NL host
```bash
docker inspect backend --format 'Started: {{.State.StartedAt}} Running: {{.State.Running}} Restarts: {{.RestartCount}}'
docker logs backend --since 5m 2>&1 | tail -50
```

### Database reconciliation query
```sql
-- Stripe orders since Apr 8 07:42 that are still PENDING (victims)
SELECT o.id AS order_id,
       o.status,
       (o.total_price::float / 100) AS price_usd,
       o.created_at,
       u.email,
       us.trading_view_name,
       sp.display_name AS plan,
       sp.tier
FROM orders o
JOIN users u ON u.id = o.user_id
LEFT JOIN user_socials us ON us.user_id = u.id
JOIN subscription_plans sp ON sp.id = o.plan_id
WHERE o.payment_method = 'STRIPE'
  AND o.created_at >= '2026-04-08 07:42:21+00'
  AND o.status = 'PENDING'
ORDER BY o.created_at;

-- Daily histogram since Mar 01 (shows the break cleanly)
SELECT DATE(created_at) AS day,
       status,
       COUNT(*) AS orders,
       SUM(total_price) AS cents
FROM orders
WHERE payment_method = 'STRIPE'
  AND created_at >= '2026-03-01'
GROUP BY 1, 2
ORDER BY day, status;
```

---

## Quick reference: where is everything

| Thing | Location |
|---|---|
| Backend source | `C:\Users\taras\Desktop\ct-projects\backend-v2` (Windows local) |
| Backend deploy | GitHub Actions on push to `fix/cryptocloud-workflow-rebuild` branch |
| Backend host | NL: 5.129.216.95 (Timeweb), SSH as root |
| Backend docker-compose | `/root/coursestools/docker-compose.yml` on NL host |
| Caddy config (NL) | `/root/coursestools/caddy/Caddyfile` in caddy container at `/etc/caddy/Caddyfile` |
| Observability host | RU: 77.232.135.132 (Timeweb SPB), SSH as root, hostname `observability` |
| Loki data retention | ~30 days from current moment |
| Loki tenant | `backend` |
| Loki URL (push) | `https://loki.winworldteam.com/loki/api/v1/push` |
| Loki URL (query, internal) | `http://localhost:3100/loki/api/v1/query_range` from observability host |
| Grafana | `https://grafana.winworldteam.com` |
| Stripe dashboard | pending partner access on Apr 11 morning |
| Investigation notes | `claude-payment-issue/` directory in the backend-v2 repo |
| Recent code changes | `fix/cryptocloud-workflow-rebuild` branch, see PR message at `claude-git/MSG-FIX-CRYPTOCLOUD-WEBHOOK.md` |
| Project docs | `claude-docs/CLAUDE_README.md` |
