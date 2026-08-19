# Admin web (admin-web)

Separate React admin panel (Vite + TypeScript + React Router v7,
feature-sliced) that consumes this backend's `/api/v1/admin` API. Direction:
**they consume us** — the backend never calls admin-web.

## Where it lives

| What | Where |
|---|---|
| Repo on operator's laptop | `C:/Users/taras/Desktop/ct-projects/admin-web` |
| Prod deploy | `/root/coursestools/admin-web` on the backend VPS `5.129.216.95` (operator-stated; not verifiable from this repo — see `../operations/servers.md`) |
| Runtime dependency direction | admin-web (browser) → `https://…/api/v1/admin/**` on this backend |
| Its docs workspace | `admin-web/claude-docs/`, `admin-web/claude-plans/`, inbox: `admin-web/claude-msgs/` |

## What they consume

All under `/api/v1/admin` (`controller/AdminController.java:40`), JWT with
role `ADMIN` required (`@PreAuthorize("hasRole('ADMIN')")` per endpoint;
`GET /statistics` also allows `PARTNER`, `AdminController.java:49`):

- `GET /statistics`, `GET /statistics/plans-by-tier`,
  `GET /statistics/plans-purchased` — dashboard stats
  (`AdminController.java:48-69`).
- `POST /access/classic`, `POST /access/custom` — grant classic / custom
  (incl. lifetime) subscription access (`AdminController.java:71-81`).
- `PATCH /users/partnership/cashback`, `GET /users` (by email / TradingView
  name / partner code) (`AdminController.java:83-98`).
- `POST /invoices/create` — custom Stripe one-time invoice
  (`AdminController.java:100-104`; fulfillment via the
  `checkout.session.completed` webhook, see `payment-gateways.md`).
- `GET /orders` — paginated order list (`AdminController.java:106-113`).
- `GET/POST/DELETE /tv-retry/jobs*` — TradingView retry queue admin
  (`AdminController.java:115-138`; behavior in `tv-access-bot.md`).

Plus shared non-admin surfaces: auth/refresh endpoints for login, and the
OpenAPI schema itself.

**Contract mechanism:** admin-web generates a type-safe client from our
OpenAPI schema at `http://localhost:8080/api/api-docs.yaml`
(`admin-web/CLAUDE.md`, "npm run api" → `src/shared/api/schema/generated.ts`).
So any change to admin DTOs/endpoints is a breaking contract change for them
until they regenerate.

## Contract message history

Past API contracts communicated to the admin-web agent live in
`../../history/inbox/` (this repo): `MSG-ADMIN-WEB-ACCESS-CLASSIC-AND-CUSTOM.md`,
`MSG-ADMIN-WEB-CUSTOM-CASHBACK.md`, `MSG-ADMIN-WEB-LIFETIME-ACCESS-REPLY.md`,
`MSG-ADMIN-WEB-ORDERS-PAGE.md`, `MSG-ADMIN-WEB-PLANS-BY-TIER-REPLY.md`,
`MSG-ADMIN-WEB-PLANS-PURCHASED-FIX.md`,
`MSG-ADMIN-WEB-PLAN-PURCHASE-DISTRIBUTION-REPLY.md`,
`MSG-ADMIN-WEB-TV-RETRY-QUEUE.md`. Their replies/requests to us:
`admin-web/claude-msgs/MSG-BACKEND-*.md`. These are historical snapshots —
the OpenAPI schema and `AdminController` are authoritative.

## Binding invariants on our side

- Admin endpoints are protected by `@PreAuthorize` role checks, not by URL
  patterns — a new admin endpoint MUST carry its own `@PreAuthorize`
  annotation (`controller/AdminController.java`).
- Keep the OpenAPI schema accurate (springdoc generates it from controllers/
  DTOs); admin-web's client is generated from it verbatim.
- Backend admin domain docs: `../architecture/admin.md`.

## Sending work to their agents

Their inbox path: `C:/Users/taras/Desktop/ct-projects/admin-web/claude-msgs/`.

Format: see `../../protocols/messaging.md` "Cross-project messages" section.
Declare `Response path:` back into this repo's `../../history/inbox/` (or the
llm-corner agent inbox once adopted).

## Change history

- 2026-07-12 — file created from verified code (both repos read).
