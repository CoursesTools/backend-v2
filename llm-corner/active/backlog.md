# Backlog — Known Open Items

Deferred work, accepted audit findings, and confirmed TODO/FIXMEs
that have no active plan. One line each, severity-tagged, with
source. Items leave this list by: shipping (note the commit),
becoming a PLAN-NNN, or being explicitly dropped (note why).

The session-close checklist (`../protocols/lifecycle.md`) feeds
this file; don't let findings live only inside audit reports.

## Open

<!-- Severity: [Critical] [High] [Medium] [Low] [Process]
Format:
- **[Severity]** <one-line description>. Source: <where it came from>.
-->

- **[High]** Remaining fallout from the `winworldteam.com` expiry. Stripe
  recovery is done: all missed events were replayed on 10-25. Still open:
  - Check CryptoCloud for gap-window (10-20 → 10-25) postbacks.
  - Contact the order-857 customer, who cancelled over lost access.
  - Leave the DEAD TV row for sub 4999 alone.
  - Repoint the Stripe endpoint `we_1PblclGJrMWbNU7AbaoGNVih` to
    `api.coursestools.com`, and keep winworldteam on auto-renew.
  Source: handoff `2026-10-25-winworldteam-domain-expiry-webhooks.md`.
- **[Critical]** ~~Unpaid Stripe renewals got a free month~~ Fixed on branch
  `fix/stripe-paid-invoice-only-extension` (DEC-005), **not yet deployed**.
  The operator chose to revert the 4 leaked renewals (orders 1034, 731,
  1040, 29). V18 does it on deploy, guarded per invoice. **After deploy the
  operator cancels their TV access, and user 3074's, manually.** Source:
  11-05 investigation.
- **[High]** Admin TV force-retry replays a stored DEAD payload without
  re-checking the subscription (`TradingViewRetryService.forceRetry` →
  `processDueJobs`).
  - 2026-11-05 08:09: job 487 granted user 3074 PRO-tier access until 11-25
    (`exp=2026-11-25T14:43:40`). Their Stripe sub was cancelled for
    non-payment on 11-01 (local sub 4999 TERMINATED), after 5 failed charges.
  - Force-retry should refuse, or warn, when the subscription is no longer
    active.
  Source: 11-05 investigation.
- **[Critical]** Alert delivery loses messages daily. 24h on 11-05: 265
  Telegram connect timeouts, 47 DB connection drops, and every failure dropped
  the alert batch. Cause: the RU host's ISP blackholes 15–45% of connections
  to every reachable Telegram IP, and the bot had a 75s timeout and no retry.
  - Code fix **deployed** 11-05: alert-bot-v2 PR #1, alert-router PR #2.
  - Durable fix decided: Amsterdam relay (PLAN-003). The scripts are ready
    in `ct-projects/ops-scripts/` and the **operator must run** them (agents
    may not write to remote hosts).
  - Monitoring: the backend `alerts.*` ops checks (`docs/architecture/ops-alerts.md`).
  - The hourly 400s were the expiry job sending a null `telegramId`. Fixed
    in backend (confirmation only on subscribe).
  See `docs/ecosystem/alert-bot.md`. Source: 11-05 investigation.
- **[High]** Backend `ALERT_BOT_URL` points at 193.160.209.73, not the
  delivering bot on `ct-alert-vps` (`alerts.tw1.su`). That IP is referenced
  only in backend `.env` and is unreachable, so subscription confirmations
  never arrive. Repoint: `ops-scripts/2026-11-05-backend-env.sh` (operator
  runs, PLAN-003 step 3). Source: 11-05 investigation.
- **[High]** Possible alert-catalog mismatches with silent non-delivery:
  - `Crossing_equlibrium` arrives tagged WCSMC (about 5.5k/day "not found"),
    but V8 moved it to `smctb`.
  - `EQH`/`EQL` arrive tagged SMCTB with no `smctb` rows.

  Verify (read-only; agent DB access was denied):
  `SELECT indicator, event, count(*) FROM alerts WHERE event IN
  ('crossing_equlibrium','eqh','eql') GROUP BY 1,2;` and
  `SELECT a.indicator, a.event, count(*) subs, count(DISTINCT ua.user_id)
  users FROM users_alerts ua JOIN alerts a ON a.id = ua.alert_id WHERE
  a.event IN ('crossing_equlibrium','eqh','eql') GROUP BY 1,2;`.
  Subscribers under an indicator that never arrives are getting nothing.
  Source: 11-05 investigation.
- **[High]** Cancelled Stripe customers kept their alerts. A Stripe deletion
  goes straight to TERMINATED and never ran the alert removal (only the
  GRANTED → GRACE job removes alerts), and the bot reads `users_alerts` live.
  Fixed going forward on `fix/stripe-paid-invoice-only-extension`: the
  deletion webhook now removes alerts. Once the PLAN-003 bot deploys, the
  existing leftover rows are also no longer delivered (status filter, C18).
  The cleanup below is now only housekeeping (step 0 of the release counts
  them). Count first: `SELECT count(DISTINCT ua.user_id) users,
  count(*) rows FROM users_alerts ua WHERE NOT EXISTS (SELECT 1 FROM
  users_subscriptions us JOIN subscription_plans sp ON sp.id = us.plan_id
  JOIN subscription_types st ON st.id = sp.subscription_type_id WHERE
  us.user_id = ua.user_id AND st.name = 'COURSESTOOLS' AND us.status <>
  'TERMINATED');`. Then run the same `WHERE` as a `DELETE`. Source: 11-05
  review.
- ~~**[Decision]** Dunning wipes alerts at GRACE~~ Resolved by DEC-006:
  grace pauses alerts for every payment method, and the list is deleted only
  at termination (uncommitted, PLAN-003).
- **[Low]** `LocalDateTime` ↔ `timestamptz` goes through Hibernate in the
  JVM default zone. Prod runs UTC, so this is correct, but any non-UTC run
  shifts instants. Consider `spring.jpa.properties.hibernate.jdbc.time_zone:
  UTC` (a no-op in prod, but it touches every entity: test carefully).
  Source: PLAN-003 local browser pass, 11-05.
- **[Low]** Read-time `discardPastGracePeriodSubscription` terminates without
  removing `users_alerts` (DEC-006 known gap). The bot filter already mutes
  those users. Fix by moving alert removal onto a `GRACE_PERIOD_END` /
  `TRIAL_ENDED` listener if the inert rows ever matter. Source: PLAN-003
  docs pass.
- **[Medium]** About 144 sends/day fail with `chat not found`. These users
  linked a Telegram id but never pressed /start, and silently get no alerts.
  Surface it to them (site banner) and to admin. Source: 11-05 investigation.
- **[High]** Alert-bot and router endpoints are unauthenticated:
  `https://alerts.tw1.su/api/alert` publicly accepts alert injection. Code
  ready (PLAN-003, `feat/alert-security-and-stats` in both repos). Closed
  only after the operator updates the 2 watchlist URLs and runs
  `ops-scripts/2026-11-05-alert-security-flips.sh`. Source: 11-05 alert-repo
  review.
- **[Medium]** The alert-router deploy script ignores a failed
  `docker compose pull` (no `set -e`), so the workflow reports success while
  the old container keeps running. Seen 2026-11-05, GHCR `NotFound: content
  digest` right after push; fixed by re-running the deploy job. Add
  `set -euo pipefail` and a pull retry. The backend and bot scripts are
  likely the same; check them. Source: 11-05 deploy.
- **[Medium]** PLAN-002 follow-ups never confirmed: scheduler cleanup,
  order #997 repair, and Direct Extend verified in prod (archived plan,
  `history/plans/`). Source: plan-cap housekeeping 11-05.
- **[Low]** Prod `/root/coursestools/.env` is mode 644 (world-readable). If
  the CI deploy user is root, tighten it to 600. Check `SSH_USER` first, or
  deploys may break. Source: 11-05.
- **[Medium]** PRO trial → Essentials purchase reads as "paid but access
  ends at trial end". The trial grants every indicator; Essentials grants
  only `wcsmc`, so all the other indicators lapse when the trial ends
  (user 5561 on 11-05). Needs clearer copy at checkout and/or an upgrade
  path. Source: 11-05 investigation.
- **[High]** `coursestools.com` (the main domain) expires 2026-12-19
  10:43 UTC per Verisign RDAP. Confirm Namecheap auto-renew is on. Source:
  same handoff.
- **[Medium]** ~~No alerting on webhook silence or domain expiry~~ Covered by
  PLAN-003 ops checks (Stripe endpoint probe, undelivered events, RDAP, TLS,
  Loki freshness), pending deploy. Still open: move the prod Loki push URL
  off the legacy `winworldteam.com` domain (needs a backend container
  recreate, operator-gated). Source: same handoff.
- **[High]** The TradingView access service currently acknowledges that an
  `/open` command was written to a shared `open.txt`, not that the command was
  durably queued or applied. Concurrent commands can overwrite each other and
  HTTP 2xx is not proof of granted access. Bot owner is implementing an
  explicit per-request result/queue contract; backend response enforcement is
  blocked until that rollout is confirmed. Source: August 2026 trial recovery
  incident and controlled whole-second probe, 2026-08-21.
- **[Medium]** `OrderScheduler` is an empty stub — its legacy TODO asks
  for cleanup of orders never paid within some window, so unpaid orders
  currently accumulate forever (`scheduler/OrderScheduler.java:10`).
  Source: repo TODO sweep at corner bootstrap, 2026-07-12.
- **[Low]** Four more legacy Russian-language TODOs in prod code predate
  the no-TODO convention: user-count scaling watch
  (`scheduler/SubscriptionScheduler.java:17`), balance-payment redirect
  link (`service/payment/impl/BalancePaymentService.java:31`),
  pre-expiry reminder emails at 3/7 days
  (`service/SubscriptionDeactivationService.java:42`). (The fifth,
  `SubscriptionScheduler.java:18`, became PLAN-001 item 5.) Source:
  repo TODO sweep at corner bootstrap, 2026-07-12.

## How items leave this list

- **Shipped:** struck through with the commit SHA, then deleted at
  the next session-close sweep.
- **Promoted to plan:** moved to `active/plans/PLAN-NNN-<slug>.md`.
- **Dropped:** struck through with a one-line "why dropped",
  deleted at next sweep.
