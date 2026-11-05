# 2026-10-25 — winworldteam.com expired: Stripe webhooks + Loki shipping dead

Author: Claude Code (Opus 5.5) — operator-requested incident investigation
Commits: none — docs edits uncommitted, awaiting operator review

## State of the world

`winworldteam.com` expired at the registrar on **2026-10-20 19:55:05 UTC**
(Namecheap RDAP: `registrar expiration 2026-10-20T19:55:05`). Namecheap moved
it to its expired-domain nameservers (`dns101/dns102.registrar-servers.com`),
whose zone serves a parking lander: every `*.winworldteam.com` name now
resolves to `104.219.250.37` (Namecheap parking, Phoenix) and `2.59.170.20`
(WorldStream NL), TTL 60, instead of prod `5.129.216.95`. The registry's
`expiration 2027-10-20` is Verisign's automatic auto-renew-grace bump, not a
paid renewal. Status is only `client transfer prohibited` (no redemption /
pending-delete), so renewal in the Namecheap account is still routine — but
the grace window is about a month, after which a redemption fee applies and the
domain can be lost.

Stripe's reported "TLS error" + "could not connect" matches exactly what the
two parking IPs do for `https://api.winworldteam.com/...`:

- `104.219.250.37:443` → `schannel: failed to receive handshake, SSL/TLS
  connection failed` (openssl: `unexpected eof while reading`); port 80 serves
  a "parked domain" market page.
- `2.59.170.20:443` → `Connection timed out` (no listener).

The healthy host is fine: `POST https://api.coursestools.com/api/v1/payments/stripe`
with a bogus signature returns Spring's `400 {"error":"Security error",
"message":"No signatures found matching the expected signature for payload"}`;
`.../crypto` with a fake full postback returns `400 "Token not valid"`. Its
cert history (Cert Spotter CT log) has no gap: 2026-08-24 → 2026-11-22, renewed
early with 2026-10-22 → 2027-01-20.

**Inference, not yet verified:** the Stripe endpoint URL is on
`api.winworldteam.com`. Nothing in the repo or the April audits records the
configured URL (`2026-04-payment-webhook-investigation-summary.md:90,213` say
UNKNOWN). It's still the only explanation that fits: `api.coursestools.com`
never served a bad cert and answers correctly. Confirm in the Stripe dashboard.

**Collateral damage from the same expiry:**

- **CryptoCloud postbacks**: the April audit confirmed the callback URL as
  `https://api.winworldteam.com/api/v1/payments/crypto`. Unless it was moved
  since, crypto payments since 10-20 are also unprocessed.
- **Loki log shipping**: the backend's Docker Loki driver pushes to
  `loki.winworldteam.com`. The last backend line in Loki is
  **2026-10-20 21:35:00 UTC**. Everything after that exists only in
  `docker logs backend` on the box, and only as far back as the driver's local
  cache keeps it. The "last Stripe webhook" Loki shows (2026-10-20 15:25:09 UTC,
  `invoice.payment_succeeded`) is only the last one *we can see*.
- `grafana.` / `prometheus.winworldteam.com` are dead. Grafana and Loki still
  work by IP (`http://77.232.135.132:3000`, `:3100/ready` → `ready`).
- Frontend: no functional dependency (only a TradingView profile link and legal
  copy mention WinWorldTeam).

## Update 2026-10-25 ~17:30 UTC — domain renewed, endpoint confirmed

- **Domain renewed** at 2026-10-25 16:31:20 UTC (Namecheap RDAP `last
  changed`; registrar expiration now 2027-10-20).
  - NS is back on Timeweb. `api.` and the apex resolve to `5.129.216.95`;
    `loki.` and `grafana.` resolve to `77.232.135.132`.
  - Existing certs are still valid (`api.winworldteam.com` until 2026-11-20).
  - Both payment routes on `api.winworldteam.com` reach Spring again.
- **Loki gap is permanent**: there are no backend lines between
  2026-10-20 21:35 and 2026-10-25 16:45 UTC. They were not backfilled.
- **Stripe endpoint confirmed** with operator-approved, read-only API GETs:
  - `we_1PblclGJrMWbNU7AbaoGNVih`, `enabled`, URL
    `https://api.winworldteam.com/api/v1/payments/stripe`.
  - Subscribed events: `invoice.payment_succeeded`,
    `checkout.session.completed`, `customer.subscription.updated/deleted`.
  - A second endpoint, `we_1Nvi65…` → `forms.tildacdn.com` (Tilda,
    all events), is not ours. When resending, pick ours only.
  - `pending_webhooks` counts both endpoints. Tilda-only types
    (`invoice.paid`, `charge.succeeded`) all show `pending=0`, so
    `pending=1` on our four types means *our* endpoint never got it.
- **Why one order recovered and another didn't**: Stripe auto-retries a
  live event for about 3 days.
  - Order 1058 (user 643, `satish_35`) was paid 10-25 11:39, still inside
    its window. The 17:03:12 auto-retry processed it and TV activation
    succeeded.
  - Customer `cus_UYxhU6S8X8suT8` (order 857, sub
    `sub_1TZpm4GJrMWbNU7Az01QPGHE`, $14.90 monthly renewal) is the other
    case. Invoice `in_1UTIhjGJrMWbNU7AiYVLDLCX` was paid 10-22 10:55:02.
    Its event `evt_1UTJeJGJrMWbNU7AtsuWson8` ran out of retries about
    10-25 10:55, roughly 5.5h before DNS came back. It will never arrive on
    its own.
  - Locally, that customer's sub went to GRACE_PERIOD after 10-22 09:53 and
    TV access lapsed. On 10-23 05:46 they set `cancel_at_period_end=true`
    in the billing portal with the comment "Cant use the indicator even tho
    i paid". **Churn caused by the outage**; the operator should contact
    them.
- **Undelivered events to our endpoint since 10-20 19:00**
  (`pending_webhooks>0`). Resend payments first, then lifecycle events,
  oldest first.
  - Payments, retries exhausted (manual resend required):
    - `evt_1UTAYFGJrMWbNU7ARem4ikNk`: 10-22 01:12, renewal $10.00.
    - `evt_1UTHYXGJrMWbNU7ArWdjlYZJ`: 10-22 08:41, **new** sub $39.90.
      Its order is PENDING and the customer never got access.
    - `evt_1UTJeJGJrMWbNU7AtsuWson8`: 10-22 10:55, renewal $14.90 (order
      857 above).
  - Payment, still auto-retrying until about 10-27 08:45:
    `evt_1UU0ZyGJrMWbNU7ATsRXT4cq` (10-24 08:45, renewal $10.00).
  - Lifecycle: 11 × `customer.subscription.updated`, 2 ×
    `customer.subscription.deleted` (`evt_1UT3IqGJrMWbNU7AVhDOcYNY` 10-21,
    `evt_1UTjdFGJrMWbNU7A5dfiwnQm` 10-23). Also 2 subscription-mode
    `checkout.session.completed`, which the backend ignores by design.
  - Order matters:
    - Replay `invoice.payment_succeeded` before that subscription's
      `.updated` events. The invoice path takes GRACE_PERIOD → RESTORED
      with the payment buffer, the PURCHASE transaction and referral
      cashback. `.updated` only syncs expiry/metadata with EXACT policy
      (`SubscriptionService.java:236-258`).
    - Replay `.updated` events oldest first, because each one carries a
      snapshot (cancel flags).
- **CryptoCloud not checked**: it also targets `api.winworldteam.com`, and
  crypto orders from the gap may be stuck. Verify in the CryptoCloud
  dashboard.

## Recovery executed 2026-10-25 17:33–17:52 UTC (operator-approved resends)

Events were resent through Stripe `POST /v1/events/{id}/retry` with
`webhook_endpoint=we_1PblclGJrMWbNU7AbaoGNVih` (ours only, never Tilda). The
sender ran one event at a time and waited for `pending_webhooks=0` plus a
settle delay, so TV `/open` commands never overlapped.

- **Payments, all processed with TV activation succeeded:**
  - Order 857 / user 4775: ESSENTIALS until 2026-11-23T09:53:48.
  - Order 28 / user 142: PRO until 2026-11-23T00:11:09.
  - Order 1056 / user 688 (new purchase): PRO until 2026-11-23T08:40:55.
  - Order 4 / user 223: PRO until 2026-11-25T07:44:04.
  - Order 1058 / user 643 had already self-healed via Stripe's 17:03
    auto-retry.
- **Lifecycle, oldest first:**
  - Subscriptions synced: 5236, 141, 5277, 4508 (×3, ends on
    `cancel_at_period_end=true`), 219, 5276.
  - Terminated by `customer.subscription.deleted`: 5091 and 164.
  - Both subscription-mode `checkout.session.completed` events were ignored
    by design.
  - No sync moved an expiry, so no TV commands were sent.
- **Final state:** zero events with `pending_webhooks>0` for our four types
  since 10-20 19:00. Since 17:30 the only backend errors are one malformed
  manual test POST and the DEAD row below.
- **Quirk:** a `/retry` call sometimes returns 200 but the delivery lands
  20–45s later, or not at all. In that case re-issue it (payments are deduped
  by invoice id, lifecycle sync is idempotent). Don't treat the 200 as
  delivered; poll `pending_webhooks`.
- **Two `past_due` events for sub 4999 / user 3074 / order 1000** (renewal
  invoice `open`, Stripe retries the charge 2026-10-27 23:44) were held back.
  Stripe's own auto-retry delivered the 14:43 period-roll event anyway at
  17:43:10.
  - The sync set local `expiredAt` to 2026-11-25T14:43:40.
  - The TV command was moved to DEAD (`currentStatus=GRACE_PERIOD`).
  - **Do not force-retry that DEAD row**: it would grant a month without
    payment. If the 10-27 charge succeeds, the live payment webhook restores
    access properly.

## Shipped

- Docs only, uncommitted: this handoff and its INDEX row; gotcha "A lapsed
  legacy domain silently killed webhooks and log shipping"; backlog items;
  dead-hostname warnings in `docs/operations/servers.md` and
  `docs/operations/log-playbook.md`.

## Open / deferred (operator actions, in order)

1. **Stripe dashboard → Developers → Webhooks → endpoint**: read the URL. If
   it's on `api.winworldteam.com`, **edit the existing endpoint's URL in place**
   to `https://api.coursestools.com/api/v1/payments/stripe`. Editing keeps the
   signing secret. Creating a new endpoint mints a new `whsec_` and forces a
   prod `STRIPE_WEBHOOK_SECRET` change plus a backend restart. Re-enable the
   endpoint if Stripe auto-disabled it after days of failures.
2. **CryptoCloud dashboard**: move the postback URL to
   `https://api.coursestools.com/api/v1/payments/crypto`.
3. **Renew `winworldteam.com` in Namecheap now**, then restore its DNS
   records (`api`, `loki`, `grafana`, `prometheus`, apex). Renew it even after
   moving the webhooks, because Loki shipping depends on it until the prod
   compose's Loki URL is changed (that's a prod container recreate, which is
   operator-gated). Turn on auto-renew.
4. **Replay**: resend ONE failed `invoice.payment_succeeded` from Stripe and
   confirm `Processing Stripe webhook` → order processed in
   `docker logs backend` (NOT Loki, which is blind until step 3), then
   batch-resend the rest. Include `customer.subscription.updated/deleted` and
   `checkout.session.completed`. Replays are safe: the invoice-id dedupe and
   row locks are described in `docs/architecture/payments.md`. Stripe's
   automatic retries last about 3 days, so events from 10-20 to about 10-22
   have already stopped retrying and need a manual resend.
5. **Victim set**: list PENDING Stripe/Crypto orders created since
   2026-10-20 19:55 UTC with the DB-truth SQL in
   `history/audits/2026-04-11-codex-payment-webhook-audit.md`, using the new
   start time.
6. `coursestools.com` itself expires **2026-12-19 10:43 UTC**
   (Verisign RDAP). Confirm auto-renew before it takes down everything.

## Landmines for the next session

- **Deadline about 2026-10-27/28.** Renewals that were missed leave
  `expiredAt` un-extended. The hourly cron moves them to `GRACE_PERIOD` (the TV
  bot access has already lapsed at expiry + 1d). Past-grace reconciliation then
  terminates them at `expiredAt <= now − 7d`. Payment processing looks up the
  current sub with `getCurrentUserSubBySubTypeId`, which excludes TERMINATED
  (`service/OrderService.java:135`,
  `service/user/UserSubscriptionService.java:33-36`):
  - Replay **before** termination takes the clean `GRACE_PERIOD → RESTORED`
    path.
  - Replay **after** termination creates a new subscription row (`CREATED`)
    instead, which means more churn, a referral deactivate/reactivate, and
    history split across rows.
- If replay slips, the interim lever for already-lapsed TV access is an admin
  grant. For Stripe-backed subs, prefer replay, because manual expiry edits on
  Stripe-managed subs are rejected (`ensureNotStripeManaged`).
- Prod Caddy will keep failing ACME renewals for its `*.winworldteam.com` site
  blocks while the domain is parked. That's noise, not an outage. Don't touch
  Caddy.

## Verification record

Exercised from the operator's machine (read-only, no prod mutation):
- DNS-over-HTTPS A/NS/SOA for `winworldteam.com`, `api.`, `loki.`, and
  `api.coursestools.com`.
- Verisign and Namecheap RDAP for both domains.
- ipinfo for both parking IPs.
- curl/openssl TLS probes on both parking IPs and on `api.coursestools.com`.
- Fake-signature POSTs to both payment routes.
- Cert Spotter cert history.
- Loki `query_range` for the last backend line and `Processing Stripe webhook`.

**NOT verified:**
- The actual Stripe endpoint URL and its status (dashboard).
- The CryptoCloud callback URL as of today.
- Backend-side `docker logs` after 10-20 21:35 UTC. SSH to ct-backend was
  denied by the session permission classifier.
- The victim order list (no DB access).
