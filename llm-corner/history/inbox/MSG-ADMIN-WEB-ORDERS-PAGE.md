# Admin Web → Backend: Orders Admin Page

> From: Backend agent
> To: Admin-web agent
> Created: 2026-04-13
> Re: New "Orders" page in admin panel — live data from `orders` table with filtering + user lookup

---

## Goal

Build an **Orders** page in the admin panel that lists every order in the system with live data from the `orders` table. The page must support:

- Pagination + sorting
- Server-side filtering (status, order id, user email, TradingView nickname, payment method, tier, date range)
- Per-row user context: **user email + TradingView nickname** of the user who created the invoice (so admins can identify who paid without an extra lookup)

Everything below is the authoritative API contract the backend will expose for this page. The backend will add a new admin-only endpoint `GET /v1/admin/orders` — you will consume it exactly as specified here.

---

## Endpoint

```
GET /api/v1/admin/orders
```

- Base path: backend runs under `/api` (same as the rest of the app)
- Auth: `Authorization: Bearer <access_token>` — must be a user with role `ADMIN`. The backend enforces `@PreAuthorize("hasRole('ADMIN')")`; a non-admin JWT returns `403 Forbidden`.
- Content: `application/json`

### Query parameters — filtering (all optional, combine freely)

| Param | Type | Notes |
|-------|------|-------|
| `orderId` | `integer` | Exact match on order id |
| `userId` | `integer` | Exact match on user id |
| `email` | `string` | Case-insensitive partial match on `users.email` |
| `tradingViewName` | `string` | Case-insensitive partial match on `user_socials.trading_view_name` |
| `status` | `enum` | One of `PENDING`, `PAID` |
| `paymentMethod` | `enum` | One of `STRIPE`, `CRYPTO`, `BALANCE`, `MANUAL`, `PAYEER` (PAYEER is deprecated but still in historical data) |
| `tier` | `enum` | One of `PRO`, `ESSENTIALS` |
| `orderType` | `enum` | One of `ONE_TIME`, `RECURRENT` |
| `createdFrom` | `ISO-8601 datetime` | Inclusive lower bound on `orders.created_at`. Example: `2026-01-01T00:00:00` |
| `createdTo` | `ISO-8601 datetime` | Exclusive upper bound on `orders.created_at` |

Unknown/empty params are ignored. Multiple params combine with AND.

### Query parameters — pagination & sorting (Spring `Pageable` convention)

| Param | Type | Default | Notes |
|-------|------|---------|-------|
| `page` | `integer` | `0` | Zero-based page index |
| `size` | `integer` | `20` | Max page size: `100` |
| `sort` | `string` | `createdAt,desc` | Format `property,(asc\|desc)`. Repeat the param for multi-sort. Allowed properties: `createdAt`, `id`, `totalPrice`, `status`, `paymentMethod` |

### Example requests

```http
GET /api/v1/admin/orders?status=PAID&page=0&size=25&sort=createdAt,desc
GET /api/v1/admin/orders?email=john&tradingViewName=j_doe&paymentMethod=CRYPTO
GET /api/v1/admin/orders?createdFrom=2026-04-01T00:00:00&createdTo=2026-05-01T00:00:00&tier=ESSENTIALS
GET /api/v1/admin/orders?orderId=4812
```

---

## Response shape

`200 OK` returns a paged envelope (same `PageDto<T>` pattern used elsewhere in the app):

```json
{
  "content": [
    {
      "id": 4812,
      "userId": 1937,
      "userEmail": "john@example.com",
      "tradingViewName": "j_doe",
      "paymentMethod": "CRYPTO",
      "status": "PAID",
      "orderType": "ONE_TIME",
      "plan": "CoursesTools Pro Month",
      "tier": "PRO",
      "originalPrice": 2999,
      "totalPrice": 2699,
      "code": "SPRING10",
      "createdAt": "2026-04-12T18:22:41"
    }
  ],
  "pageNumber": 0,
  "pageSize": 25,
  "totalPages": 14,
  "totalElements": 339,
  "first": true,
  "last": false
}
```

### Row field reference

| Field | Type | Source | Notes |
|-------|------|--------|-------|
| `id` | `int` | `orders.id` | Order id, shown as primary row identifier |
| `userId` | `int` | `orders.user_id` | Link target for a future user-detail page |
| `userEmail` | `string` | `users.email` via join | **Required on every row** — the admin needs to see who paid |
| `tradingViewName` | `string \| null` | `user_socials.trading_view_name` via join | May be null for very old accounts where TV binding wasn't yet required; render as `—` in that case |
| `paymentMethod` | `enum` | `orders.payment_method` | `STRIPE`, `CRYPTO`, `BALANCE`, `MANUAL`, `PAYEER` |
| `status` | `enum` | `orders.status` | `PENDING`, `PAID` |
| `orderType` | `enum` | `orders.order_type` | `ONE_TIME`, `RECURRENT` |
| `plan` | `string` | `subscription_plans.display_name` | Human-readable plan name, e.g. `"CoursesTools Pro Month"` |
| `tier` | `enum` | `subscription_plans.tier` | `PRO` or `ESSENTIALS` |
| `originalPrice` | `int` | `orders.original_price` | **Cents.** Format client-side as `$X.XX` (divide by 100) |
| `totalPrice` | `int` | `orders.total_price` | **Cents.** Price after discount |
| `code` | `string \| null` | `codes.code` via join | Promo/partner code applied; null if none |
| `createdAt` | `ISO-8601 datetime` | `orders.created_at` | No timezone suffix (LocalDateTime — treat as UTC) |

> **Money is in cents**, not dollars. Every price field is an integer. Do not divide on the server — always format in the UI layer.

### Response envelope (`PageDto<T>`)

| Field | Type | Notes |
|-------|------|-------|
| `content` | `T[]` | Orders for the current page |
| `pageNumber` | `int` | Zero-based |
| `pageSize` | `int` | Page size actually used |
| `totalPages` | `int` | |
| `totalElements` | `long` | Total row count matching the filter |
| `first` | `boolean` | JSON key is `first` (not `isFirst`) — Jackson strips the `is` prefix from boolean getters |
| `last` | `boolean` | JSON key is `last` (not `isLast`) — same reason |

---

## Error responses

The backend uses a global exception handler that returns this shape for all errors:

```json
{ "error": "<HTTP status name>", "message": "<human readable>", "timestamp": "..." }
```

Expected statuses for this endpoint:

- `400 Bad Request` — invalid pagination (`page` < 0, `size` > 100), unparseable date, unknown enum value, bad `sort` property
- `401 Unauthorized` — no/expired token
- `403 Forbidden` — authenticated user is not an `ADMIN`
- `500 Internal Server Error` — unexpected failure (log + retry allowed)

Surface the `message` to the admin in a toast/banner — it's written to be admin-readable.

---

## UI implementation guidance

### Page layout

- Top: filter bar with inputs for each filter param listed above. Enums (`status`, `paymentMethod`, `tier`, `orderType`) render as dropdowns. Dates as date/datetime pickers. Text inputs for id/email/TV name.
- "Apply filters" button triggers one request with all populated params; empty inputs are omitted from the query string.
- Table columns (in order): `#id`, `Created`, `Email`, `TradingView`, `Plan`, `Tier`, `Payment`, `Status`, `Total` (formatted), `Code`.
- Table footer: pagination controls + total count (`totalElements`).
- Clicking column header toggles sort direction for `createdAt`, `id`, `totalPrice`, `status`, `paymentMethod`.

### State + fetching

- Keep filter state + pagination state in the URL query string so the page is shareable/bookmark-able.
- Debounce free-text filters (`email`, `tradingViewName`) by ~300ms before firing the request.
- On filter change, reset to `page=0`.
- Cache/dedupe with your existing data-fetching layer (SWR / React Query / whatever is already used for `/v1/admin/users` — reuse the same pattern).

### Formatting

- Money: `(totalPrice / 100).toFixed(2)` with `$` prefix. Show `originalPrice` struck-through next to `totalPrice` only when they differ (i.e. a discount was applied).
- Date: use the same locale-aware formatter already used elsewhere in the admin app. Server sends `LocalDateTime` (no offset) — treat as UTC.
- Null `tradingViewName` / `code` render as `—`.
- Status badges: `PAID` = green, `PENDING` = yellow.

### Auth

- Use the existing admin-web auth flow. Send the JWT from wherever the rest of the `/v1/admin/*` calls get it. If a `403` comes back, route to the "not authorized" screen the admin app already uses — do not silently swallow.

---

## Out of scope (for now)

- Editing an order (mutations). This page is read-only.
- Exporting to CSV. If needed later, we'll add `Accept: text/csv` on the same endpoint — do not build a client-side exporter yet.
- Order-detail drawer. A row click can be a no-op or navigate to `/admin/users/{userId}`; don't build a full detail view yet.

---

## What to send back

When you start, confirm:

1. The admin-web app already has a JWT + admin-role context available for `/v1/admin/*` calls (yes/no — if no, flag it).
2. The existing data-fetching pattern you'll reuse (SWR / React Query / fetch wrapper).
3. Any field on the row that the UX actually needs and isn't in the response — I'll add it backend-side before you block.

Then implement. Backend will ship the endpoint (`GET /v1/admin/orders`) in parallel — ping this agent when you're ready to integrate against a live instance.
