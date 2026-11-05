# DEC-005 — Stripe access moves only on paid invoices

Status: ACCEPTED
Date: 2026-11-05
Author: Claude Code (operator-requested: "we can't afford to lose money")

## Context

Since `96a1866` (2026-05-03) `customer.subscription.updated` copied Stripe's
`current_period_end` into `expired_at` and published `EXTENDED`, which pushed
a full month of TradingView access. Stripe rolls `current_period_end` at the
start of a new period, before the renewal invoice is charged. Every renewal
was therefore granted a month of access first and charged afterwards. If the
charge failed, or was never attempted (card needs customer authentication),
the customer kept the unpaid month, because the bot has no revoke. Production
on 2026-11-05 showed four such renewals since 11-01 (orders 1034, 731, 1040,
29). A dunning-cancelled subscription (4999) also carried the unpaid expiry
into an admin force-retry.

A second, smaller hole: `invoice.payment_succeeded` used the subscription's
live `current_period_end`. An old invoice that was delivered late or replayed
after the next period had rolled would grant the newer, unpaid period.

## Options considered

1. Keep lifecycle expiry sync, but require the latest invoice to be paid
   (an extra Stripe API call per lifecycle event). Rejected: two sources of
   expiry truth, and out-of-order lifecycle events could still move dates.
2. Keep lifecycle sync for backward moves only. Rejected: Stripe does not
   guarantee event order, so a stale snapshot could cut a paying customer's
   access short. Legit shortening (plan change) is followed by its own paid
   proration invoice anyway.
3. **CHOSEN: paid invoices own access dates; lifecycle events are
   metadata-only; Stripe renewals get a settlement window.**

## Decision

- `invoice.payment_succeeded` sets expiry to the latest line `period.end` of
  **that invoice**. If an invoice has no line periods, it falls back to the
  subscription's `current_period_end` with a WARN log.
- `customer.subscription.updated` / `.deleted` record `currentPeriodEnd`,
  `stripeStatus` and `cancelAtPeriodEnd` as metadata and never change
  `expired_at` or publish `EXTENDED`. Deletion still terminates.
- The hourly expiry job holds a Stripe-backed subscription `GRANTED` until
  `expired_at + CUSTOMER_PAYMENT_EXPIRY_BUFFER_DAYS` (1 day). This is the
  same window as the TradingView payment buffer, so the normal gap between
  period roll and charge (about 1h) never sends grace emails, wipes alerts, or
  deactivates referrals. After the window, if no paid invoice has arrived, the
  sub enters grace as before.

## Consequences

- No unpaid Stripe time is ever granted, in the DB or on TradingView.
- In dunning, customers lose access when the buffer ends and regain it the
  moment a retry succeeds (GRACE_PERIOD → RESTORED).
- Free time given from the Stripe dashboard (for example editing
  `trial_end`) no longer propagates. Use admin Direct Extend for
  TradingView-only goodwill.
- `paymentProviderData.currentPeriodEnd` can now be ahead of `expired_at`.
  That means "renewal awaiting payment", logged as `awaiting paid invoice`.
- Contract C2 and C6 wording updated. Supersedes the expiry-sync part of the
  "Stripe lifecycle webhooks updated the DB but not TradingView" gotcha.
