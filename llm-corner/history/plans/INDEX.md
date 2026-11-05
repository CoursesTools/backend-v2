# Completed Plans Index

Plans moved here after completion. **Newest first.**

Status legend (in the Outcome column):
- `SHIPPED YYYY-MM-DD` — committed; commit SHAs in the row/file.
- `SUPERSEDED by PLAN-MMM` — replaced before shipping.
- `STALE` / `ABANDONED` — explicitly dropped (row notes why; do not
  resume from the file).

| Moved on | File | Outcome |
|---|---|---|

<!-- Example row:
| 2026-08-01 | `PLAN-001-tv-bot-hardening.md` | SHIPPED 2026-08-01 — one-line summary of what shipped, key commits, what was deferred (→ backlog) |
-->

| 2026-11-05 | `PLAN-003-grace-pause-ops-alerts-and-alert-security.md` | SHIPPED (backend-v2 #41 `e949d18`, #42; alert-bot-v2 #2; alert-router #3; admin-web #16; frontend-v2 #48) — grace pauses alerts, configurable grace, ops alerts + admin Settings/System Alerts, router secret URL + bot token, Amsterdam relay; follow-ups → backlog |
| 2026-11-05 | `PLAN-002-subscription-expiry-and-direct-tv-extend.md` | SHIPPED (PR #37, `3cc3d5f`) — source-aware expiry, disjoint schedulers, payment-only TV buffer, Direct Extend; prod verification + order #997 repair → backlog |

(`PLAN-001-tv-bot-hardening.md` is
still in `../../active/plans/`. Adding a row here is part of the
session-close checklist — `../../protocols/lifecycle.md`.)
