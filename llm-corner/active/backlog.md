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
- **[Critical]** ~~Unpaid Stripe renewals got a free month~~ Shipped `e949d18`
  (backend-v2#41, deployed 2026-11-05 13:19 UTC). DEC-005. V16 and V18
  reverted subs 4999, 5165, 243, 5181 and 95 to their last paid end
  (verified in prod). The operator cancelled TradingView access for users
  3074, 5402, 247, 2541 and 96 on 11-05. Source: 11-05 investigation.
- **[High]** Admin TV force-retry replays a stored DEAD payload without
  re-checking the subscription (`TradingViewRetryService.forceRetry` →
  `processDueJobs`).
  - 2026-11-05 08:09: job 487 granted user 3074 PRO-tier access until 11-25
    (`exp=2026-11-25T14:43:40`). Their Stripe sub was cancelled for
    non-payment on 11-01 (local sub 4999 TERMINATED), after 5 failed charges.
  - Force-retry should refuse, or warn, when the subscription is no longer
    active.
  Source: 11-05 investigation.
- **[Critical]** ~~Alert delivery loses messages daily~~ Shipped 2026-11-05:
  - retries/timeouts in alert-bot-v2 #1 and alert-router #2;
  - the Amsterdam Telegram relay is live (13:06 UTC), and the first cycle
    sent 4/4 with 0 failures;
  - the backend `alerts.*` ops checks watch it.

  See `docs/ecosystem/alert-bot.md`. Source: 11-05 investigation.
- **[High]** ~~Backend `ALERT_BOT_URL` points at 193.160.209.73~~ Shipped
  2026-11-05: repointed to `https://alerts.tw1.su/api/alert/message` with
  `X-Internal-Token`, effective with the backend-v2#41 deploy. Source: 11-05
  investigation.
- **[High]** `Crossing_equlibrium` alerts are never delivered. **Fix: V19
  (backend-v2#43); the operator chose WCSMC on 11-05.** Close this once
  the bot logs `Crossing_equlibrium` matches after the deploy.
  Verified in prod on 2026-11-05:
  - The catalog has it only under `smctb` (668 rows, since V8), with **9
    users / 1,156 subscriptions**.
  - TradingView sends it only tagged WCSMC: 207 per hour, all "Alert not
    found", and 0 SMCTB-tagged arrivals.

  Fix depends on which indicator really emits it:
  - if the WCSMC script emits it, re-tag the catalog rows (new migration;
    check tier permissions, since ESSENTIALS = wcsmc only);
  - if SMC ToolBox should emit it, fix the Pine script / watchlist alert to
    send `indicator=SMCTB`.

  `EQH`/`EQL` (SMCTB) have no catalog rows at all, so nobody can subscribe
  to them (harmless). Source: 11-05 investigation, read-only DB + bot logs.
- **[Low]** Leftover `users_alerts` rows of cancelled customers. Delivery
  stopped on 2026-11-05: the bot status filter (alert-bot-v2#2) mutes them,
  and Stripe deletion removes alerts going forward (backend-v2#41). The rows
  are inert: 10 users with only TERMINATED subscriptions hold 20,914 rows
  (release step 0). Cleanup is optional housekeeping. If they pay again,
  their old list resumes. Count first: `SELECT count(DISTINCT ua.user_id) users,
  count(*) rows FROM users_alerts ua WHERE NOT EXISTS (SELECT 1 FROM
  users_subscriptions us JOIN subscription_plans sp ON sp.id = us.plan_id
  JOIN subscription_types st ON st.id = sp.subscription_type_id WHERE
  us.user_id = ua.user_id AND st.name = 'COURSESTOOLS' AND us.status <>
  'TERMINATED');`. Then run the same `WHERE` as a `DELETE`. Source: 11-05
  review.
- ~~**[Decision]** Dunning wipes alerts at GRACE~~ Resolved by DEC-006:
  grace pauses alerts for every payment method, and the list is deleted only
  at termination. Shipped `e949d18` (backend-v2#41) with alert-bot-v2#2.
- **[Low]** `LocalDateTime` ↔ `timestamptz` goes through Hibernate in the
  JVM default zone. Prod runs UTC, so this is correct, but any non-UTC run
  shifts instants. Consider `spring.jpa.properties.hibernate.jdbc.time_zone:
  UTC` (a no-op in prod, but it touches every entity: test carefully).
  Source: PLAN-003 local browser pass, 11-05.
- **[Low]** A malformed `Authorization: Bearer` token (not a JWT) makes
  `JwtRequestFilter` throw a Jackson `JsonParseException`. It is logged at
  ERROR with a stack trace while the response is still 403. Anyone can spam
  ERROR logs this way (seen 2026-11-05 13:54 UTC from a security probe).
  Catch the parse failure in the filter and treat it as unauthenticated.
  Source: PLAN-003 release checks.
- **[Low]** Read-time `discardPastGracePeriodSubscription` terminates without
  removing `users_alerts` (DEC-006 known gap). The bot filter already mutes
  those users. Fix by moving alert removal onto a `GRACE_PERIOD_END` /
  `TRIAL_ENDED` listener if the inert rows ever matter. Source: PLAN-003
  docs pass.
- **[Medium]** About 144 sends/day fail with `chat not found`. These users
  linked a Telegram id but never pressed /start, and silently get no alerts.
  Surface it to them (site banner) and to admin. Source: 11-05 investigation.
- **[High]** ~~Alert-bot and router endpoints are unauthenticated~~ Shipped
  2026-11-05 (alert-bot-v2#2, alert-router#3; flips at 14:05 UTC). The old
  URL gets 404, the bot without the token gets 401, and encoded/`;param`
  bypasses get 401 (verified in prod). Source: 11-05 alert-repo review.
- **[Medium]** The alert router does not retry when the bot refuses a
  connection. Any bot restart loses the alerts that arrive meanwhile (11 lost
  in the ~3s gap of the 14:05 flip restart). Add a short retry with backoff
  on `ConnectException` in `AlertRoutingService` (alert-router). Source:
  PLAN-003 release.
- **[Medium]** The alert-router deploy script ignores a failed
  `docker compose pull` (no `set -e`), so the workflow reports success while
  the old container keeps running. Seen 2026-11-05, GHCR `NotFound: content
  digest` right after push; fixed by re-running the deploy job. Add
  `set -euo pipefail` and a pull retry. The backend and bot scripts are
  likely the same; check them. Source: 11-05 deploy.
- **[Medium]** PLAN-002 follow-ups never confirmed: scheduler cleanup,
  order #997 repair, and Direct Extend verified in prod (archived plan,
  `history/plans/`). Source: plan-cap housekeeping 11-05.
- **[Low]** ~~Prod `/root/coursestools/.env` is mode 644~~ Done 2026-11-05:
  it is now 600. The box has no non-root users and CI deploys as root; a
  backend deploy after the change succeeded. Source: 11-05.
- **[Medium]** ~~PRO trial → Essentials purchase reads as "paid but access
  ends at trial end"~~ Dropped: per the operator, the 11-05 case (user 5561,
  psentogo) was the TV access server not granting permissions, fixed on the
  bot side. No evidence of a copy problem. Source: 11-05 investigation.
- **[High]** `coursestools.com` (the main domain) expires 2026-12-19
  10:43 UTC per Verisign RDAP. Confirm Namecheap auto-renew is on. Source:
  same handoff.
- **[Medium]** ~~No alerting on webhook silence or domain expiry~~ Shipped
  2026-11-05 as PLAN-003 ops checks (Stripe endpoint probe, undelivered
  events, RDAP, TLS, Loki freshness), all verified against prod. Still open:
  move the prod Loki push URL
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
