# Ops alerts (system watchdog)

The backend checks its own payment, TradingView, alert-delivery and
infrastructure health on a schedule. It records every finding in
`ops_alerts` and sends it to the admins' Telegram through
`@ct_maintenance_bot`. Admins read the full history on the admin-web
**System Alerts** page and control delivery on the **Settings** page. Design
rationale: DEC-007.

## Contract

- **History is the source of truth.** `OpsAlertService.raise` always inserts
  an `ops_alerts` row first. Telegram is a best-effort copy:
  - delivery runs after commit, `@Async`;
  - the row's `telegram_status` records the outcome (`PENDING`, `SENT`,
    `PARTIAL`, `FAILED`, `PAUSED`, `CATEGORY_DISABLED`, `NO_RECIPIENTS`,
    `NOT_CONFIGURED`).
- **Pause and category switches affect only Telegram.** A paused or disabled
  alert is still stored and searchable. Paused alerts are dropped, never
  queued: delivery re-checks the pause flag just before sending.
- **Edge-triggered checks.** `track(checkKey, …)` keeps state in
  `ops_alert_state`:
  - one alert when a check starts failing;
  - a "Still failing:" reminder every `ops.reminder-interval` (6h);
  - one `RESOLVED` alert when it recovers.

  One-off facts (a dispute, a DEAD TV job, the daily digest) use
  `raiseOnce(dedupKey)`.
- **Settings fail open for alerting.** If `system_settings` or the category
  rows cannot be read, the backend treats alerts as not paused and categories
  as enabled. A broken settings read must never silence an outage.
- Retention: rows older than 365 days are purged daily (`ops.retention` cron).

## Checks

Crons are in `configs/scheduler.yml` under `scheduler.ops`. All checks run on
a private single-thread executor (`scheduler/OpsScheduler.java`), so a slow
probe never blocks other schedulers. A run that overlaps a busy executor is
skipped and logged. `OPS_CHECKS_ENABLED=false` disables every check except
retention.

| Category | Check key(s) | Cron | Fires when |
|---|---|---|---|
| PAYMENTS | `payments.stripe-webhook-endpoint` | every 10 min | the Stripe endpoint for `…/api/v1/payments/stripe` is missing or disabled, or an unsigned probe POST does not get the backend's `400 … signature` answer (TLS, DNS or domain failure) |
| PAYMENTS | `payments.stripe-undelivered-events` | every 10 min | Stripe still holds pending webhooks older than 30 min (last 3 days, the 4 event types we handle) |
| PAYMENTS | `payments.paid-not-processed` | every 10 min | a subscription invoice paid in the last 48h whose period end is not reflected in `users_subscriptions.expired_at` |
| PAYMENTS | `stripe-event:<id>` (once) | every 10 min | a new dispute or refund |
| TV_ACCESS | `tv-dead:<jobId>:<forceRetryCount>` (once) | every 10 min | a TradingView retry job went DEAD (needs an admin) |
| TV_ACCESS | `tv.stuck-retries` | every 10 min | PENDING retry jobs first enqueued more than 30 min ago |
| TV_ACCESS | `tv.bot-unreachable` | every 10 min | TCP connect to `urls.activating-bot` fails |
| ALERT_DELIVERY | `alerts.no-incoming` (CRITICAL) | every 5 min | the alert bot reports no TradingView alert for 30+ min |
| ALERT_DELIVERY | `alerts.delivery-failing` | every 5 min | requeued chats, stale drops or a queue above 1000 in the last 15 min |
| ALERT_DELIVERY | `alerts.bot-unreachable`, `alerts.monitoring-not-configured` | every 5 min | `GET ops.alert-bot.stats-url` fails, or no internal token is configured |
| INFRASTRUCTURE | `infra.domain:<d>`, `infra.domain-lookup:<d>` | 05:30 UTC daily | a domain in `ops.domains` expires within 30 days (critical at 7), checked via RDAP |
| INFRASTRUCTURE | `infra.tls:<host>` | 05:30 UTC daily | a TLS cert in `ops.tls-hosts` expires within 14 days (critical at 3), or the handshake fails |
| INFRASTRUCTURE | `infra.log-shipping` | every 15 min | Loki has no backend log line for 15+ min |
| INFRASTRUCTURE | "Backend started" (INFO) | on `ApplicationReadyEvent` | every deploy or restart |
| DIGEST | `digest:<day>` (once) | 09:00 Europe/Moscow | daily summary of the previous Moscow day: signups, trials, purchases, conversions, renewals awaiting payment, cancellations with comments, DEAD jobs, alert-bot 24h stats, alerts by category |

Code: `service/ops/checks/` (one class per category plus `OpsDigestService`).

## Telegram delivery

- `OpsTelegramClient` uses its own `RestTemplate` (5s connect, 20s read).
  It retries 429 (honouring `retry_after`, capped at 30s), 5xx and IO errors
  up to 3 attempts. 400 and 403 mean the recipient never pressed Start, or
  blocked the bot.
- Messages use Telegram HTML mode. `TelegramHtml.fromMarkdown` converts the
  markdown subset the checks write (headings, `- ` bullets, `code`,
  `**bold**`, `[text](https://…)`) and escapes everything else.
- Every message links to `ops.admin-web-url + /system-alerts?id=<id>`.
- The bot token is redacted from every logged error (`OpsTelegramClient.redact`).
- The backend runs in Amsterdam and reaches Telegram directly. The RU-hosted
  alert bot cannot (see `../ecosystem/alert-bot.md`).

## History and search

- `ops_alerts.search_text` is a stored generated column,
  `lower(title || ' ' || body)`, with a `pg_trgm` GIN index. V17 creates the
  extension and index inside a guarded `DO` block, so a database without
  `pg_trgm` still migrates and search falls back to a sequential scan.
- `OpsAlertSearchRepository`:
  - each whitespace-separated term (max 8, 64 chars each) becomes
    `search_text LIKE '%term%'`, AND-ed, with LIKE wildcards escaped;
  - optional category and `[from, to)` filters;
  - keyset pagination on `id DESC` (`beforeId` cursor).
- Both the list and the single-alert read map `created_at` through the same
  explicit-UTC row mapper.

## Configuration (`application.yml` → `ops:`, record `config/props/OpsProperties`)

| Env var | Default | Purpose |
|---|---|---|
| `OPS_CHECKS_ENABLED` | `true` | master switch for scheduled checks |
| `OPS_TELEGRAM_BOT_TOKEN` | empty | `@ct_maintenance_bot` token. Empty means alerts are stored as `NOT_CONFIGURED` |
| `OPS_TELEGRAM_API_BASE_URL` | `https://api.telegram.org` | override for tests or relays |
| `OPS_ADMIN_WEB_URL` | `https://admin.coursestools.com` | deep-link base in Telegram messages |
| `ALERT_BOT_STATS_URL` | `https://alerts.tw1.su/api/ops/stats` | alert-bot counters |
| `ALERT_BOT_INTERNAL_TOKEN` | empty | `X-Internal-Token` for the alert bot (stats and subscription confirmation) |
| `OPS_LOKI_URL` | `http://77.232.135.132:3100` | Loki for the log-freshness check (tenant `backend`) |

Domains and TLS hosts are lists in `application.yml` (`ops.domains`,
`ops.tls-hosts`).

## Admin control

Recipients, global pause, per-category switches and the test message are on
`/v1/admin/settings/**`. The history API is `/v1/admin/ops-alerts`. Both are
listed in `admin.md`.

## Gotchas

- A recipient must open `@ct_maintenance_bot` and press Start once, or
  Telegram rejects the message (shown as `FAILED`/`PARTIAL` with detail).
- The test message has a 10s global cooldown (409 if pressed faster).
- Checks that call Stripe use the live key and only read. The webhook probe
  is not a Stripe call: it sends an unsigned POST to our own public webhook
  URL, which the backend answers with 400 "No signatures found"
  (`GlobalExceptionHandler`, not logged).
- `ops_alert_state` is keyed by check key. Renaming a key orphans the old
  state row (harmless; it never resolves).
- Healthy checks log nothing and write nothing (only state changes are
  stored). To prove they run, query Prometheus at `77.232.135.132:9090`:
  `tasks_scheduled_execution_seconds_count{code_namespace=~".*OpsScheduler"}`.
- Building outbound URLs: a query value containing `{...}` (e.g. a LogQL
  selector) must be passed as an expanded URI variable
  (`queryParam("query", "{query}")` + `buildAndExpand`). `encode()` would
  treat it as a template and leave it unencoded, which caused the false
  Loki alert fixed in #42.
