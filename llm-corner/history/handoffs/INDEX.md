# Handoffs Index

Session-end handoff notes. **Newest first.** Always start a new
session by reading the top entry.

Format per row: filename + 1-3 sentence headline ("what this
session did + the operator-relevant next step"). The full file is
in this same directory.

## Index

<!-- New rows go at the TOP. Format:
- **`YYYY-MM-DD-<slug>.md`** — current entry point.
  <1-3 sentence summary>.
- `YYYY-MM-DD-<older-slug>.md` — previous entry point.
  <summary>.
-->

- **`2026-11-05-plan-003-released.md`** — current entry point.
  PLAN-003 is live in production: grace pauses alerts, ops alerts plus
  admin Settings/System Alerts, the Amsterdam relay, and enforced alert auth
  (flips 14:05 UTC). backend-v2#43 adds V19 (Crossing_equlibrium → WCSMC)
  and 401 for junk tokens. Operator follow-ups are done; the remaining items
  are in the backlog.
- `2026-11-05-plan-003-release-runbook.md` — previous entry point.
  PLAN-003 is built and pushed as 4 PRs, none merged:
  - grace pauses alerts;
  - configurable grace;
  - ops alerts to Telegram, plus admin-web Settings and System Alerts;
  - router secret URL and bot token;
  - V18 unpaid-renewal revert.

  Contains the operator-run release runbook: merge order bot → router →
  backend → admin-web, host env scripts in `ct-projects/ops-scripts/`, and
  the flips.
- `2026-11-05-paid-invoice-only-and-alert-delivery.md` — previous entry point.
  Stripe access now moves only on paid invoices (DEC-005). V16 restores user
  3074. Alert delivery is hardened against Telegram blocking on the RU host.
  The bot and router delivery fixes shipped the same day (PR #1, #2).
- `2026-10-25-winworldteam-domain-expiry-webhooks.md` — previous entry point.
  `winworldteam.com` expired 2026-10-20 19:55 UTC, killing the Stripe endpoint
  (confirmed on `api.winworldteam.com`), CryptoCloud postbacks and Loki
  shipping. It was renewed 10-25 16:31 UTC, and all missed Stripe events were
  replayed through the API (4 paying customers restored with TV access, zero
  pending). Still open: CryptoCloud gap check and the endpoint repoint. Loki
  has a permanent gap from 10-20 21:35 to 10-25 16:45 UTC.
- `2026-08-21-tv-whole-second-expiration.md` — previous entry point.
  PR #40 canonicalizes every TV activation/rename expiration to whole seconds
  without changing DB entitlement math; isolated recovery is proceeding one
  user at a time because the remote shared-file 2xx is not proof of access.
- **`2026-08-19-tv-activation-ordering-followup.md`** — current entry point.
  PR #37 now stages command-tokened activation snapshots so the newest
  payment/admin/Direct command wins despite async delivery order; 94 tests pass.
- `2026-08-19-subscription-expiry-and-direct-tv-extend.md` — previous entry
  point. PR #37 fixes stale-base paid expiry, makes scheduler paths disjoint,
  scopes the TV buffer to customer payments, and adds exact TV-only Direct
  Extend. Operator merge, production verification, and order #997 repair remain.
