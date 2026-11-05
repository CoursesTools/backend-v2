-- Operator-requested data repair (2026-11-05).
-- User 3074 / subscription 4999 (Stripe sub_1U8LZgGJrMWbNU7AhmYhbv0L, order 1000): the 2026-10-25
-- renewal invoice was never paid and Stripe cancelled the subscription on 2026-11-01 after failed
-- dunning. Before the paid-invoice-only fix, the unpaid period roll had moved expired_at one month
-- forward to 2026-11-25 14:43:40 UTC. Restore the last paid period end (2026-10-25 14:43:40 UTC).
-- TradingView access granted on 2026-11-05 is cancelled manually by the operator.
--
-- Guarded so it only touches this row while it is still in the leaked state; on any other
-- database (fresh/test) it updates nothing.
UPDATE users_subscriptions
SET expired_at = expired_at - INTERVAL '1 month',
    updated_at = CURRENT_TIMESTAMP
WHERE id = 4999
  AND user_id = 3074
  AND status = 'TERMINATED'
  AND payment_method = 'STRIPE'
  AND payment_provider_data ->> 'subscriptionId' = 'sub_1U8LZgGJrMWbNU7AhmYhbv0L'
  AND expired_at >= TIMESTAMPTZ '2026-11-25 00:00:00+00'
  AND expired_at <  TIMESTAMPTZ '2026-11-26 00:00:00+00';
