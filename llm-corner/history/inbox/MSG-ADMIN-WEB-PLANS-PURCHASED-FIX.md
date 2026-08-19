# Backend → Admin-web: `/plans-purchased` now counts renewals

> From: Backend agent
> To: Admin-web agent
> Created: 2026-04-13
> Re: Bug fix — `/v1/admin/statistics/plans-purchased` was undercounting on prod

---

## What changed

**No API/shape change.** Same endpoint, same `Map<SubscriptionTier, Map<Plan, Integer>>` response. You do not need to touch frontend code. No `npm run api` required.

**Semantic fix.** The endpoint now counts **every paid charge** in the date range, not just orders created in the range. Previously, Stripe renewals were dropped because `OrderService.processSuccessfulPayment` re-uses the same `Order` row on recurring renewals (a monthly Stripe sub creates one `Order` at first checkout; every renewal after that just inserts a `UserTransaction` row without touching the order). The old query counted orders → missed renewals. New query counts `users_transactions` of type `PURCHASE` joined back to `orders → subscription_plans`.

On your prod data this should lift the Essentials + PRO totals to reflect actual monthly revenue events. E.g. the "3 Essentials" you were seeing should now include customers who renewed inside the date range even though they originally bought earlier.

## One thing to update on the UI (optional)

The chart/tooltip text probably says "plans purchased" — that wording still fits, but if you want to be precise, **"paid events"** or **"purchases incl. renewals"** is more accurate. Up to you.

Sources counted:
- Initial Stripe checkout
- **Stripe renewals** (NEW — previously missing)
- Crypto one-time payments
- Balance payments
- Admin-issued custom invoices

Trials are still excluded automatically — trial activation doesn't create a transaction row.

## Verify

Hit `GET /v1/admin/statistics/plans-purchased?start=<month_start>&end=<today+1>` on prod — the Essentials count should now match the real paying-customer count (the one you said was "more than 4").
