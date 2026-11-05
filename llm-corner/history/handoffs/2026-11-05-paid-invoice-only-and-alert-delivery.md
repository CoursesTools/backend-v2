# 2026-11-05 — Paid-invoice-only Stripe access, user 3074 repair, alert delivery

Author: Claude Code (Opus 5.5)
Commits: none. Uncommitted, awaiting operator approval to commit/push:

- backend-v2 branch `fix/stripe-paid-invoice-only-extension`. It also
  carries the 10-25 incident docs.
- alert-bot-v2 branch `fix/reliable-telegram-delivery`.
- alert-router branch `fix/per-bot-webclient`.

## State of the world

- **Stripe (DEC-005).**
  - Since May, a renewal's period roll extended access before payment. Four
    unpaid renewals since 11-01 (orders 1034, 731, 1040, 29) got a free month.
  - The fix makes paid invoices the only thing that moves Stripe expiry, to
    that invoice's own line period. Lifecycle webhooks become metadata-only,
    and Stripe subs get a 1-day settlement window before grace.
  - Without that window, the ~1h gap before Stripe charges a renewal would
    have sent paying users the expiry email and wiped their alerts.
- **User 3074 (subscription 4999).** Stripe cancelled it 11-01 after failed
  dunning. The operator force-retried its DEAD TV job 487 at 08:09 UTC, and
  the bot applied it (colleague's screenshot: 3 scripts OK at 11:09 MSK).
  V16 restores expiry 11-25 → 10-25 (the last paid period end). It is guarded
  and was verified on a throwaway Postgres 16 (target reverted, decoy
  untouched, idempotent). The operator cancels TV access manually.
- **Alerts.** The production bot runs on `ct-alert-vps` (37.77.105.213, St
  Petersburg). It loses alerts every day:
  - 265 Telegram connect timeouts in 24h;
  - 47 DB connection drops, each losing an alert for all its subscribers;
  - 144 `chat not found`;
  - 10 blocked.

  Only `149.154.167.32` / `.132` / `.220` reach the Bot API from that host,
  at 85% / 55% / 75% (40 tries each); DNS's `149.154.166.110` and IPv6 are
  fully blocked. The 75s library connect timeout serialized the stalls.
  - Backend fix: the confirmation is sent only after subscribe. This ends the
    hourly 400s (null `telegramId` from the expiry job) and the false
    "successfully subscribed" messages sent as alerts were wiped.
  - Bot fix: 5s connect timeout, classified retries (network/429/5xx),
    requeue with outage abort, thread-safe buffer, 4096-char chunking, DB
    retry, proxy and base-URL switches.
  - Router fix: per-bot WebClients (the shared mutable builder could misroute
    concurrent alerts).
- **psentogo (user 5561, order 1063).** Bought Essentials ($14.90, `wcsmc`
  only) after a PRO trial. The backend sent ESSENTIALS until 12-06 at 04:16
  UTC (07:16 MSK); the earlier 03:34 attempt was declined. The colleague's
  bot log screenshot starts at 11:09 MSK, so it neither shows nor rules out
  his 07:16 command. Also absent: hafiza17's trial at 09:17 MSK.

- **Cancelled Stripe customers kept their alerts.** A deletion goes straight
  to TERMINATED and never ran the alert removal. `PaymentFacade` now revokes
  alerts on Stripe deletion. Existing leftovers need the cleanup SQL in the
  backlog, and the dunning alert-wipe trade-off is an operator decision
  (backlog `[Decision]`).

## Verification record

- **backend-v2:** `./gradlew build --rerun-tasks` passed, **117 tests, 0
  failures/errors/skips**. That includes new tests for:
  - lifecycle metadata-only behaviour;
  - deletion after dunning keeping the paid expiry and returning the user;
  - a late invoice granting only its own period;
  - the settlement window (inside, after, non-Stripe);
  - alert unsubscribe not notifying the bot;
  - the facade revoking alerts only on an applicable Stripe deletion.

  No Spring/DB integration test exists, so V16 was applied by hand to a fresh
  Postgres 16 container after V1–V15.
- **alert-bot-v2:** `./gradlew build` passed, 13 new tests. They cover
  concurrent buffer integrity, requeue order, chunking, failure
  classification, and the scheduler's requeue and abort. A Spring wiring test
  boots `TelegramService`, the cache and the scheduler against the real
  `application.yml` defaults and asserts the timeouts; the class that
  registers the bot with Telegram is left out. The classification test caught
  a real bug: network failures carry `errorCode = 0`, not null. `.gitignore`
  `AlertBot` matched the package dir on case-insensitive checkouts and hid
  every new file. It is now anchored to `/AlertBot`.
- **alert-router:** `./gradlew build` passed: context-load test plus the new
  concurrent per-bot routing test.
- **Prod reads:** Loki; Stripe read-only API (operator approved in-session);
  ssh `ct-alert-vps` for docker ps/logs, Telegram probes and Caddy routes.
- **Denied by the permission classifier:** a read-only SQL against the
  shared DB, so the catalog mismatch is unverified.

## Open / deferred

- Deploy decisions for the three branches. A backend push to master is a
  prod deploy; V16 runs on startup.
- The four leaked unpaid renewals: revert (guarded migration) and cancel TV
  manually, or leave? → backlog.
- Durable Telegram egress (proxy on the Amsterdam host vs moving the bot).
  Backend `ALERT_BOT_URL` host mismatch. Catalog mismatch check →
  backlog.
- Monitoring/admin proposal: `agents/claude/notes/2026-11-05-ops-watchdog-and-admin-proposal.md`.

## Landmines

- After the Stripe fix deploys, `paymentProviderData.currentPeriodEnd` can
  legitimately be ahead of `expired_at`; that means "renewal awaiting
  payment". Don't "fix" it.
- Free days given in the Stripe dashboard no longer propagate. Use admin
  Direct Extend.
- The alert-bot compose `extra_hosts` pin (`api.telegram.org:149.154.167.220`)
  lives only on the host. `.32` tested best today; changing it is a host
  edit.
- Bot deploy recreates the container: up to one scheduler interval of
  buffered alerts is lost on restart (the buffer is in memory).
