# DEC-007 — Ops alerts are always stored; settings are admin-only and audited

Status: ACCEPTED
Date: 2026-11-05
Author: Claude Code (operator decisions in the PLAN-003 session)

## Context

Several production incidents in October and November 2026 were noticed only
when customers complained, or when the operator asked an agent to look:

- the `winworldteam.com` expiry broke Stripe webhooks;
- unpaid Stripe renewals were granted access;
- TradingView alerts silently failed to reach Telegram.

The operator wants the system to tell admins in Telegram, without flooding
them, and wants to control that from admin-web instead of asking an agent.

## Decision

- **Store first, send second.** Every finding is an `ops_alerts` row. Telegram
  is a copy, sent after commit. Pause and per-category switches only stop
  the copy. History is kept for one year and searchable in admin-web.
- **Paused means dropped.** Nothing is queued while paused, so resuming
  never floods. Paused alerts are still in history.
- **Edge-triggered.** Continuous conditions alert once, remind every 6h,
  and resolve once. One-off facts are de-duplicated by key.
- **Fail open.** If settings cannot be read, alerts count as not paused and
  categories as enabled. Grace days fall back to 7 (DEC-006).
- **Dedicated bot.** `@ct_maintenance_bot`, sending from the Amsterdam
  backend, which reaches Telegram directly. Recipients are Telegram user ids
  managed in admin-web (max 20).
- **Security of the controls:**
  - every `/v1/admin/settings/**` and `/v1/admin/ops-alerts/**` method
    carries `@PreAuthorize("hasRole('ADMIN')")`, with no PARTNER exception.
    `AdminEndpointSecurityTest` fails the build if a method on
    `AdminSettingsController` or `AdminOpsAlertController` lacks it;
  - the actor is taken from the JWT principal, never from the request
    body;
  - inputs are bean-validated (grace 1–30, Telegram id `^[1-9][0-9]{4,14}$`,
    label ≤ 64 chars without `<>`). The DB re-checks with CHECK
    constraints;
  - grace changes carry the settings `version` (optimistic lock, 409 on a
    stale page);
  - every change is written to `admin_audit_log` with before/after JSON and
    the acting admin;
  - the bot token comes from env only, never from the DB or the API, and
    is redacted from logs. The API exposes only whether it is configured.

## Consequences

- A new settings or system-alerts endpoint without the ADMIN annotation
  breaks the build. `AdminController` is not covered, because its
  `GET /statistics` deliberately allows PARTNER.
- The audit trail covers only the Settings page. Older admin actions
  (grants, cashback, TV retry) remain unaudited (`../docs/security/baseline.md`).
- Ops checks add outbound traffic from prod: Stripe reads, RDAP, TLS
  handshakes, Loki and the alert-bot stats. `OPS_CHECKS_ENABLED=false` turns
  it all off.
