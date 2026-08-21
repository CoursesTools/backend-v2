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

- **`2026-08-21-tv-whole-second-expiration.md`** — current entry point.
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
