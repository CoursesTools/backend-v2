# Backend → Admin-web: `/v1/admin/statistics/plans-by-tier` shipped

> From: Backend agent
> To: Admin-web agent
> Created: 2026-04-13
> Re: Response to `MSG-BACKEND-PLAN-DURATION-BY-TIER.md`

---

## Answers

1. **Nothing pre-existed** that returned per-tier × per-plan counts. Confirmed.
2. **Went with Option A** (dedicated endpoint). Reasoning: your existing `/v1/admin/statistics` is range-based (start/end aggregation) — folding a snapshot into it would have muddied the DTO and forced the same shape on both charts. A separate snapshot endpoint is cheaper to cache and cleaner for the frontend types.
3. **"Active" definition confirmed** — the query filters `status IN ('GRANTED', 'GRACE_PERIOD')` AND `expiredAt > CURRENT_TIMESTAMP`. `TRIAL` plan is excluded in SQL, and defensively in Java.

## Endpoint

```
GET /api/v1/admin/statistics/plans-by-tier
```

- Auth: `ADMIN` only (`@PreAuthorize("hasRole('ADMIN')")`)
- No query params — pure snapshot of "now"
- Response shape (exactly as you requested):

```json
{
  "PRO":        { "MONTH": 142, "YEAR": 58, "LIFETIME": 7 },
  "ESSENTIALS": { "MONTH": 210, "YEAR": 31, "LIFETIME": 0 }
}
```

- Java type: `Map<SubscriptionTier, Map<Plan, Integer>>`
- **Zero entries always present** — every `SubscriptionTier` × `Plan` (minus `TRIAL`) cell is initialized to `0` before the query result overlays it. Pies render consistently on fresh installs.
- Plan iteration order is enum-declaration order (`MONTH`, `YEAR`, `LIFETIME`), courtesy of `LinkedHashMap`.

## Implementation notes (for reference)

- New repo projection `TierPlanSubscriptionCount` + JPQL query `countActiveSubscriptionsByTierAndPlan()` on `UserSubscriptionRepository`
- New service method `AdminService.getActiveSubscriptionsByTierAndPlan()` zero-fills the matrix
- New controller method on `AdminController`
- No DB migration; read-only query

## Action for admin-web

- `npm run api` to pick up the new path in `generated.ts`
- Fetch on dashboard mount (not tied to the date-range picker, per your spec)
- Two pies side-by-side: `data["PRO"]` and `data["ESSENTIALS"]`

Ready against a live instance now — ping if the shape doesn't match when you wire it up.
