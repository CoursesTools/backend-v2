-- Operator-requested data repair (2026-11-05, PLAN-003).
-- Before DEC-005, Stripe's period roll extended these four subscriptions one month ahead although
-- the renewal invoice was never paid (verified in Stripe on 2026-11-05: each renewal invoice is
-- still `open`). Restore each one's last paid period end. The operator cancels their TradingView
-- access manually.
--
-- Guards: a row changes only while it still carries the leaked expiry AND its last processed invoice
-- is still the last paid one. If the customer pays before this runs, the payment replaces invoiceId
-- and the row is left alone. On any other database this updates nothing.

-- Order 1034, user 5402: last paid in_1ULh0CGJrMWbNU7A3ZsNym9J (period end 2026-11-01 10:14:12 UTC)
UPDATE users_subscriptions
SET expired_at = expired_at - INTERVAL '1 month',
    updated_at = CURRENT_TIMESTAMP
WHERE id = 5165
  AND user_id = 5402
  AND payment_method = 'STRIPE'
  AND payment_provider_data ->> 'subscriptionId' = 'sub_1ULh0CGJrMWbNU7AwOiZ0Dza'
  AND payment_provider_data ->> 'invoiceId' = 'in_1ULh0CGJrMWbNU7A3ZsNym9J'
  AND expired_at >= TIMESTAMPTZ '2026-12-01 00:00:00+00'
  AND expired_at < TIMESTAMPTZ '2026-12-02 00:00:00+00';

-- Order 731, user 247: last paid in_1UMSQhGJrMWbNU7AtGJeA4ZR (period end 2026-11-03 12:52:00 UTC)
UPDATE users_subscriptions
SET expired_at = expired_at - INTERVAL '1 month',
    updated_at = CURRENT_TIMESTAMP
WHERE id = 243
  AND user_id = 247
  AND payment_method = 'STRIPE'
  AND payment_provider_data ->> 'subscriptionId' = 'sub_1T6sQeGJrMWbNU7AhYfH2B9h'
  AND payment_provider_data ->> 'invoiceId' = 'in_1UMSQhGJrMWbNU7AtGJeA4ZR'
  AND expired_at >= TIMESTAMPTZ '2026-12-03 00:00:00+00'
  AND expired_at < TIMESTAMPTZ '2026-12-04 00:00:00+00';

-- Order 1040, user 2541: last paid in_1UMka9GJrMWbNU7ApMroVxkL (period end 2026-11-04 08:15:41 UTC)
UPDATE users_subscriptions
SET expired_at = expired_at - INTERVAL '1 month',
    updated_at = CURRENT_TIMESTAMP
WHERE id = 5181
  AND user_id = 2541
  AND payment_method = 'STRIPE'
  AND payment_provider_data ->> 'subscriptionId' = 'sub_1UMka9GJrMWbNU7AIar89q5n'
  AND payment_provider_data ->> 'invoiceId' = 'in_1UMka9GJrMWbNU7ApMroVxkL'
  AND expired_at >= TIMESTAMPTZ '2026-12-04 00:00:00+00'
  AND expired_at < TIMESTAMPTZ '2026-12-05 00:00:00+00';

-- Order 29, user 96: last paid in_1UMtH9GJrMWbNU7AuXCCMxwn (period end 2026-11-04 17:32:09 UTC)
UPDATE users_subscriptions
SET expired_at = expired_at - INTERVAL '1 month',
    updated_at = CURRENT_TIMESTAMP
WHERE id = 95
  AND user_id = 96
  AND payment_method = 'STRIPE'
  AND payment_provider_data ->> 'subscriptionId' = 'sub_1QHU8LGJrMWbNU7AbxLtEKYg'
  AND payment_provider_data ->> 'invoiceId' = 'in_1UMtH9GJrMWbNU7AuXCCMxwn'
  AND expired_at >= TIMESTAMPTZ '2026-12-04 00:00:00+00'
  AND expired_at < TIMESTAMPTZ '2026-12-05 00:00:00+00';
