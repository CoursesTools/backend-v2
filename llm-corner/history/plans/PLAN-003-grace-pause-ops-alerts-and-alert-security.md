# PLAN-003 — Grace pauses alerts, ops alerts + settings in admin-web, alert security

**Status:** SHIPPED 2026-11-05 (released to production the same day)
**Owner:** Claude Code (chief)
**Repos:** backend-v2, alert-bot-v2, alert-router, admin-web
(frontend-v2 gets a handoff message)

## Decisions (operator, 2026-11-05)

- **Grace pauses alerts.** Grace never deletes `users_alerts`; full expiry
  does, and so does a Stripe cancellation or a trial ending, but only when
  the user has no other active subscription. Payment resumes delivery.
- **Grace days configurable in admin-web.** Default 7, allowed 1–30.
  Fallback to 7 on any read failure or invalid value.
- **Admin Telegram alerts** via the dedicated bot `@ct_maintenance_bot`
  (token in prod `.env` as `OPS_TELEGRAM_BOT_TOKEN`, added 2026-11-05).
  - Recipients are editable; seeded with `639072017`.
  - Global Pause/Play: paused means dropped, never queued.
  - Per-category on/off.
  - **Everything is always saved to history**; pause and off only affect
    Telegram.
- Categories: PAYMENTS, TV_ACCESS, ALERT_DELIVERY, INFRASTRUCTURE, DIGEST
  (daily at 09:00 MSK).
- **History page in admin-web:**
  - per-category view;
  - instant search on each keystroke, with matches highlighted;
  - markdown rendering;
  - date filters.

  Retention is 1 year.
- **Router auth:** secret in the TradingView webhook URL. There are only 2
  watchlist alerts, so the cut-over is coordinated. The legacy path stays
  enabled only until the operator has updated both alerts.
- **Telegram egress for the alert bot** goes through Amsterdam (relay on
  the prod Caddy).
- **Revert the 4 unpaid renewals** (orders 1034, 731, 1040, 29); the
  operator cancels their TV access manually.
- Shipping: bot and router delivery fixes went out 2026-11-05. Everything
  else ships as one release.

## Contracts

| Contract | Value |
|---|---|
| Bot internal auth header | `X-Internal-Token`. The bot accepts tokens from env `ALERT_BOT_INTERNAL_TOKENS` (comma-separated, for rotation) and enforces them when `ALERT_BOT_REQUIRE_TOKEN=true` |
| Router → bot | router sends `X-Internal-Token: ${ALERT_BOT_INTERNAL_TOKEN}` |
| Backend → bot | `ALERT_BOT_URL=https://alerts.tw1.su/api/alert/message`; backend sends `X-Internal-Token: ${ALERT_BOT_INTERNAL_TOKEN}` |
| Bot stats | `GET /api/ops/stats` (token required): counters for the last 15 min and 24h: `alertsReceived`, `messagesSent`, `sendAttemptsFailed`, `chatsRequeued`, `alertsDroppedStale`, `permanentRejections`, `queueSize`, `lastAlertReceivedAt`, `lastMessageSentAt`, `startedAt` |
| Bot delivery filter | subscriber queries require an active CoursesTools subscription (`users_subscriptions.status IN ('GRANTED','PENDING')`) |
| Router webhook | `POST /api/alerts/{secret}`. The secret must be in `ALERT_ROUTER_WEBHOOK_SECRETS` (constant-time compare). The legacy `POST /api/alerts` works only while `ALERT_ROUTER_ALLOW_LEGACY_PATH=true`. Unknown secret → 404 |
| Relay | `https://api.coursestools.com/tg-relay/*` → `https://api.telegram.org/*`, allowed only from 37.77.105.213, access logging skipped. Bot env `TELEGRAM_API_BASE_URL=https://api.coursestools.com/tg-relay/bot` |

## Backend design

- **Tables** (all in `V17__system_settings_ops_alerts_audit.sql`):
  - `system_settings` (single row, CHECK `grace_period_days` 1–30,
    `ops_alerts_paused`, `version`);
  - `ops_alert_categories` (`enabled`);
  - `ops_telegram_recipients` (`telegram_id > 0`, unique, max 20 enforced in
    service);
  - `ops_alerts` (history: generated lower-case `search_text`, `pg_trgm` GIN
    index created only if the extension can be installed, so startup can
    never fail; btree on `created_at` and `(category, id DESC)`);
  - `ops_alert_state` (per check key: open/resolved, last notified);
  - `admin_audit_log`.
- Settings are read fresh on each use (single-row PK read, no cache) and
  fall back to 7 / defaults on any failure.
- **Ops alerts:**
  - always persisted;
  - edge-triggered (open → reminder every 6h → resolved);
  - Telegram HTML delivery with retries;
  - the token is redacted from every logged error.
- **Admin API** under `/v1/admin/settings/**` and `/v1/admin/ops-alerts`:
  - `@PreAuthorize("hasRole('ADMIN')")` only;
  - bean validation;
  - optimistic locking;
  - every change written to `admin_audit_log`.
- **Data fix:** a guarded migration reverts the 4 renewals while each is
  still unpaid (guard: last processed `invoiceId` unchanged).

## Rollout order

1. Relay on the Amsterdam Caddy, then the bot env switch.
2. Bot: filter, token (not yet required), stats.
3. Router: secret path, legacy still allowed, sends the token.
4. Backend, admin-web.
5. The operator updates the 2 watchlist URLs.
6. Flip `ALERT_ROUTER_ALLOW_LEGACY_PATH=false` and
   `ALERT_BOT_REQUIRE_TOKEN=true`.
7. Hand the grace display off to frontend-v2.

## Progress

- [x] Bot reliable delivery deployed (alert-bot-v2 PR #1)
- [x] Router per-bot clients deployed (alert-router PR #2, deploy re-run after GHCR pull race)
- [x] `OPS_TELEGRAM_BOT_TOKEN` in prod `.env`; Amsterdam → Telegram 20/20
- [x] Relay + bot env switch, live 13:06 UTC. The operator granted host
  write access; scripts in `ct-projects/ops-scripts/2026-11-05-*.sh`.
- [x] Bot: filter/token/stats (alert-bot-v2 `feat/alert-security-and-stats`,
  22 tests) · Router: secret path + token (alert-router same branch, 6 tests).
  Committed locally, not pushed.
- [x] Backend: grace pause, settings, ops alerts + checks + digest, admin
  API, V16–V18. Gate green (156 tests). Booted locally against PG16, with an
  E2E API pass (403/400/409/404 paths, audit, search, paging) and a browser
  pass. Uncommitted on `fix/stripe-paid-invoice-only-extension`.
- [x] admin-web: Settings page, System Alerts page (`feat/settings-and-system-alerts`,
  `bun run build` green, browser-verified). Uncommitted.
- [x] Docs: DEC-006, DEC-007, C13/C14/C18, `ops-alerts.md`, admin,
  subscriptions, payments, persistence, security, alert-bot, admin-web,
  changelog.
- [x] Secrets generated into `ct-projects/ops-scripts/secrets/` (never
  printed). Host env scripts written.
- [x] PRs open: alert-bot-v2#2, alert-router#3, backend-v2#41, admin-web#16.
  Bot token-filter bypass found in review and fixed (`6116d4e`).
- [x] frontend-v2 handoff: `frontend-v2/claude-tasks/GRACE-PERIOD-FROM-API.md`.
- [x] Released 2026-11-05: bot, router, backend (#41, #42), admin-web,
  frontend-v2#48. Flips at 14:05 UTC. Record:
  `history/handoffs/2026-11-05-plan-003-released.md`.
- [x] Plan closed and archived.
