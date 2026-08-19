# Docker DB Access

## Connect

```bash
docker exec -it postgres psql -U postgres -d coursestools
```

## Backend container logs

```bash
# Follow live logs (Ctrl+C to stop)
docker logs -f backend

# Last 100 lines
docker logs backend --tail 100

# Last 100 lines, follow new output live
docker logs backend --tail 100 -f

# Filter for errors only
docker logs backend --tail 200 2>&1 | grep -iE "ERROR|Exception|Caused by|FATAL"

# Check if OOM-killed by OS
dmesg | grep -i "oom\|killed" | tail -5

# Shell into running container
docker exec -it backend sh
```

## Useful commands

| Command | Description |
|---------|-------------|
| `\dt` | List all tables |
| `\d table_name` | Describe table structure |
| `\q` | Exit psql |
| `SELECT * FROM table_name LIMIT 10;` | Preview table data |
| `TRUNCATE table_name CASCADE;` | Delete all rows (cascades to related tables) |

## Link Telegram to user

```sql
UPDATE user_socials
SET telegram_id = 'YOUR_TELEGRAM_ID', updated_at = NOW()
WHERE user_id = (SELECT id FROM users WHERE email = 'test123@gmail.com');
```

## Subscription Plans & Tiers

```sql
-- View all plans with tiers
SELECT sp.id, sp.name, sp.display_name, sp.price, sp.tier, st.name as subscription_type
FROM subscription_plans sp
JOIN subscription_types st ON st.id = sp.subscription_type_id
ORDER BY sp.tier, sp.id;

-- View tier indicator permissions (which indicators each tier can use)
SELECT tip.tier, tip.indicator, st.name as subscription_type
FROM tier_indicator_permissions tip
JOIN subscription_types st ON st.id = tip.subscription_type_id;
```

## Subscriptions

```sql
-- Check current subscriptions
SELECT us.*, sp.name as plan_name, sp.tier FROM users_subscriptions us
JOIN subscription_plans sp ON sp.id = us.plan_id
WHERE us.user_id = 3;

-- Terminate current subscription
UPDATE users_subscriptions
SET status = 'TERMINATED', updated_at = NOW()
WHERE user_id = 3 AND status != 'TERMINATED';

-- Add PRO monthly subscription ($29.99, adjust expired_at as needed)
INSERT INTO users_subscriptions (user_id, plan_id, price, payment_method, status, is_trial, expired_at)
SELECT 3, sp.id, 2999, 'STRIPE', 'GRANTED', false, '2026-05-07T00:00:00+00'
FROM subscription_plans sp WHERE sp.name = 'MONTH' AND sp.tier = 'PRO';

-- Add ESSENTIALS monthly subscription ($14.90)
INSERT INTO users_subscriptions (user_id, plan_id, price, payment_method, status, is_trial, expired_at)
SELECT 3, sp.id, 1490, 'STRIPE', 'GRANTED', false, '2026-05-07T00:00:00+00'
FROM subscription_plans sp WHERE sp.name = 'MONTH' AND sp.tier = 'ESSENTIALS';

-- Add PRO lifetime subscription
INSERT INTO users_subscriptions (user_id, plan_id, price, payment_method, status, is_trial, expired_at)
SELECT 3, sp.id, 0, 'STRIPE', 'GRANTED', false, '2099-12-31T00:00:00+00'
FROM subscription_plans sp WHERE sp.name = 'LIFETIME' AND sp.tier = 'PRO';

-- Add ESSENTIALS lifetime subscription
INSERT INTO users_subscriptions (user_id, plan_id, price, payment_method, status, is_trial, expired_at)
SELECT 3, sp.id, 0, 'STRIPE', 'GRANTED', false, '2099-12-31T00:00:00+00'
FROM subscription_plans sp WHERE sp.name = 'LIFETIME' AND sp.tier = 'ESSENTIALS';

-- ===== Add subscription by email (replace EMAIL and TIER) =====

-- Add MONTH subscription by email
INSERT INTO users_subscriptions (user_id, plan_id, price, payment_method, status, is_trial, expired_at)
SELECT u.id, sp.id, sp.price, 'MANUAL', 'GRANTED', false, NOW() + INTERVAL '30 days'
FROM users u, subscription_plans sp
WHERE u.email = 'EMAIL_HERE' AND sp.name = 'MONTH' AND sp.tier = 'TIER_HERE';

-- Add YEAR subscription by email
INSERT INTO users_subscriptions (user_id, plan_id, price, payment_method, status, is_trial, expired_at)
SELECT u.id, sp.id, sp.price, 'MANUAL', 'GRANTED', false, NOW() + INTERVAL '365 days'
FROM users u, subscription_plans sp
WHERE u.email = 'EMAIL_HERE' AND sp.name = 'YEAR' AND sp.tier = 'TIER_HERE';

-- Add LIFETIME subscription by email
INSERT INTO users_subscriptions (user_id, plan_id, price, payment_method, status, is_trial, expired_at)
SELECT u.id, sp.id, sp.price, 'MANUAL', 'GRANTED', false, '2099-12-31T00:00:00+00'
FROM users u, subscription_plans sp
WHERE u.email = 'EMAIL_HERE' AND sp.name = 'LIFETIME' AND sp.tier = 'TIER_HERE';
```

> **Usage:** Replace `EMAIL_HERE` with the user's email and `TIER_HERE` with `PRO` or `ESSENTIALS`.

## Promo codes

```sql
-- View all promo codes with tier scope
SELECT c.id, c.code, c.discount_value, c.discount_type, c.tier, c.valid_until, c.max_uses
FROM codes c WHERE c.owner_id IS NULL;

-- Universal promo code (works for any tier)
INSERT INTO codes (code, discount_value, discount_type, valid_until, max_uses)
VALUES ('TESTPROMO', 10.00, 'PERCENTAGE', '2027-01-01', NULL);

-- PRO-only promo codes
INSERT INTO codes (code, discount_value, discount_type, valid_until, max_uses, tier, subscription_type_id)
VALUES ('PRO15OFF', 15.00, 'PERCENTAGE', '2027-01-01', NULL, 'PRO',
        (SELECT id FROM subscription_types WHERE name = 'COURSESTOOLS'));

INSERT INTO codes (code, discount_value, discount_type, valid_until, max_uses, tier, subscription_type_id)
VALUES ('PROVIP', 500, 'FIXED', '2027-01-01', 50, 'PRO',
        (SELECT id FROM subscription_types WHERE name = 'COURSESTOOLS'));

-- ESSENTIALS-only promo codes
INSERT INTO codes (code, discount_value, discount_type, valid_until, max_uses, tier, subscription_type_id)
VALUES ('ESS20OFF', 20.00, 'PERCENTAGE', '2027-01-01', NULL, 'ESSENTIALS',
        (SELECT id FROM subscription_types WHERE name = 'COURSESTOOLS'));

INSERT INTO codes (code, discount_value, discount_type, valid_until, max_uses, tier, subscription_type_id)
VALUES ('ESSDEAL', 300, 'FIXED', '2027-01-01', 100, 'ESSENTIALS',
        (SELECT id FROM subscription_types WHERE name = 'COURSESTOOLS'));
```

## Orders (payment history)

```sql
-- Populate sample orders for a user (replace user_id 3 with your user's id)
-- Mix of tiers, durations, statuses, and payment methods

-- Essentials Month — paid via Stripe
INSERT INTO orders (user_id, plan_id, payment_method, original_price, total_price, status, order_type, created_at, updated_at)
SELECT 3, sp.id, 'STRIPE', 1490, 1490, 'PAID', 'RECURRENT', '2026-03-01T10:00:00+00', '2026-03-01T10:05:00+00'
FROM subscription_plans sp WHERE sp.name = 'MONTH' AND sp.tier = 'ESSENTIALS';

-- Essentials Year — paid via Crypto
INSERT INTO orders (user_id, plan_id, payment_method, original_price, total_price, status, order_type, created_at, updated_at)
SELECT 3, sp.id, 'CRYPTO', 11950, 11950, 'PAID', 'ONE_TIME', '2026-03-05T14:30:00+00', '2026-03-05T14:35:00+00'
FROM subscription_plans sp WHERE sp.name = 'YEAR' AND sp.tier = 'ESSENTIALS';

-- Essentials Lifetime — paid via Balance
INSERT INTO orders (user_id, plan_id, payment_method, original_price, total_price, status, order_type, created_at, updated_at)
SELECT 3, sp.id, 'BALANCE', 19930, 19930, 'PAID', 'ONE_TIME', '2026-03-10T09:00:00+00', '2026-03-10T09:00:00+00'
FROM subscription_plans sp WHERE sp.name = 'LIFETIME' AND sp.tier = 'ESSENTIALS';

-- Pro Month — paid via Stripe
INSERT INTO orders (user_id, plan_id, payment_method, original_price, total_price, status, order_type, created_at, updated_at)
SELECT 3, sp.id, 'STRIPE', 2999, 2999, 'PAID', 'RECURRENT', '2026-03-15T11:00:00+00', '2026-03-15T11:02:00+00'
FROM subscription_plans sp WHERE sp.name = 'MONTH' AND sp.tier = 'PRO';

-- Pro Year — paid via Crypto, with discount applied
INSERT INTO orders (user_id, plan_id, payment_method, original_price, total_price, status, order_type, created_at, updated_at)
SELECT 3, sp.id, 'CRYPTO', 28999, 24649, 'PAID', 'ONE_TIME', '2026-03-20T16:00:00+00', '2026-03-20T16:10:00+00'
FROM subscription_plans sp WHERE sp.name = 'YEAR' AND sp.tier = 'PRO';

-- Pro Lifetime — pending (not yet paid)
INSERT INTO orders (user_id, plan_id, payment_method, original_price, total_price, status, order_type, created_at, updated_at)
SELECT 3, sp.id, 'CRYPTO', 48000, 48000, 'PENDING', 'ONE_TIME', '2026-03-25T08:00:00+00', '2026-03-25T08:00:00+00'
FROM subscription_plans sp WHERE sp.name = 'LIFETIME' AND sp.tier = 'PRO';
```

## Export active clients (all tiers + trials)

```sql
-- JSON array of all users with an active subscription (GRANTED, not expired),
-- including trials, across every tier. Fields: tradingview, expired_at.
SELECT jsonb_agg(
         jsonb_build_object(
           'tradingview', us_soc.trading_view_name,
           'expired_at', us.expired_at
         )
         ORDER BY us.expired_at
       ) AS clients
FROM users_subscriptions us
JOIN users u          ON u.id = us.user_id
JOIN user_socials us_soc ON us_soc.user_id = u.id
WHERE us.status = 'GRANTED'
  AND us.expired_at > NOW()
  AND us_soc.trading_view_name IS NOT NULL;
```

## Alerts

```sql
-- Repopulate alerts (wipe existing + subscriptions first)
TRUNCATE alerts CASCADE;
-- Then run tmp_alerts_insert.sql

-- Check alert counts
SELECT type, broker, COUNT(*) FROM alerts GROUP BY type, broker ORDER BY type, broker;
```

## News

```sql
INSERT INTO news (title, content, created_at, updated_at) VALUES
('Platform Maintenance Complete', 'All systems are back online. Thanks for your patience.', '2026-03-28 09:00:00+00', '2026-03-28 09:00:00+00'),
('New Crypto Pairs Added', 'We have added SUIUSDT and TONUSDT to alerts. Enable them in your dashboard.', '2026-03-29 11:00:00+00', '2026-03-29 11:00:00+00'),
('Improved Alert Delivery Speed', 'We have optimized our alert processing pipeline. Telegram notifications now arrive within 1-2 seconds of the signal firing on TradingView. This applies to all timeframes and market types. Let us know if you notice any issues.', '2026-03-29 18:00:00+00', '2026-03-29 18:00:00+00'),
('March 2026 Product Update', 'This month we shipped several major features. The new Invoices page lets you view your full payment history with filtering and pagination. We also expanded our asset coverage to 137 instruments across forex, crypto, indices, stocks, and CFDs. Alert delivery latency was reduced by 40%. Partnership dashboard now shows real-time earnings. We are working on multi-alert support coming in April.', '2026-03-30 08:00:00+00', '2026-03-30 08:00:00+00'),
('Getting Started with WCSMC Alerts', 'WCSMC alerts help you catch key market structure shifts in real time. Start by connecting your Telegram in Settings. Then go to the Alerts page and pick your market type, broker, and assets. Select the events you want to track — BOS and ChoCh are great for beginners. Choose your timeframes and hit subscribe. You will receive Telegram notifications whenever the selected events trigger. Pro tip: start with 1h and 4h timeframes to avoid noise from lower intervals.', '2026-03-30 14:00:00+00', '2026-03-30 14:00:00+00');
```

## Delete user by email

```sql
-- Check user before deleting
SELECT id, email, role FROM users WHERE email = 'EMAIL_HERE';

-- Delete all related records then the user (replace EMAIL_HERE)
DO $$
DECLARE uid INT;
BEGIN
  SELECT id INTO uid FROM users WHERE email = 'EMAIL_HERE';
  DELETE FROM users_alerts WHERE user_id = uid;
  DELETE FROM referrals_earnings WHERE referral_id IN (SELECT id FROM referrals WHERE referrer_id = uid OR referred_id = uid);
  DELETE FROM referrals WHERE referrer_id = uid OR referred_id = uid;
  DELETE FROM codes_usages WHERE user_id = uid;
  DELETE FROM codes WHERE owner_id = uid;
  DELETE FROM users_transactions WHERE user_id = uid;
  DELETE FROM orders WHERE user_id = uid;
  DELETE FROM users_subscriptions WHERE user_id = uid;
  DELETE FROM user_partnership WHERE user_id = uid;
  DELETE FROM user_socials WHERE user_id = uid;
  DELETE FROM trial_activations WHERE user_id = uid;
  DELETE FROM user_profile WHERE user_id = uid;
  DELETE FROM users WHERE id = uid;
END $$;
```

## Delete user by TradingView nickname

```sql
-- Check user before deleting
SELECT u.id, u.email, u.role, us.trading_view_name
FROM users u JOIN user_socials us ON us.user_id = u.id
WHERE us.trading_view_name = 'TV_NAME_HERE';

-- Delete all related records then the user (replace TV_NAME_HERE)
DO $$
DECLARE uid INT;
BEGIN
  SELECT user_id INTO uid FROM user_socials WHERE trading_view_name = 'TV_NAME_HERE';
  DELETE FROM users_alerts WHERE user_id = uid;
  DELETE FROM referrals_earnings WHERE referral_id IN (SELECT id FROM referrals WHERE referrer_id = uid OR referred_id = uid);
  DELETE FROM referrals WHERE referrer_id = uid OR referred_id = uid;
  DELETE FROM codes_usages WHERE user_id = uid;
  DELETE FROM codes WHERE owner_id = uid;
  DELETE FROM users_transactions WHERE user_id = uid;
  DELETE FROM orders WHERE user_id = uid;
  DELETE FROM users_subscriptions WHERE user_id = uid;
  DELETE FROM user_partnership WHERE user_id = uid;
  DELETE FROM user_socials WHERE user_id = uid;
  DELETE FROM trial_activations WHERE user_id = uid;
  DELETE FROM user_profile WHERE user_id = uid;
  DELETE FROM user_finance WHERE user_id = uid;
  DELETE FROM users WHERE id = uid;
END $$;
```

## Make user an admin

```sql
-- Roles: USER | ADMIN | PARTNER
UPDATE users
SET role = 'ADMIN', updated_at = NOW()
WHERE email = 'EMAIL_HERE';
```

> **Note:** Role is embedded in the JWT token. The user must log out and log back in for the new role to take effect.

## Normalize TradingView names to lowercase (one-off)

TradingView treats usernames as case-insensitive. New bindings are written
lowercase; legacy rows may be mixed case. Code lookups use `IgnoreCase`, so
this is optional — run it to keep stored data consistent.

```sql
-- Preview how many rows would change
SELECT COUNT(*) FROM user_socials
WHERE trading_view_name IS NOT NULL
  AND trading_view_name <> LOWER(trading_view_name);

-- Apply
UPDATE user_socials
SET trading_view_name = LOWER(trading_view_name), updated_at = NOW()
WHERE trading_view_name IS NOT NULL
  AND trading_view_name <> LOWER(trading_view_name);

-- trial_activations already stores/compares via LOWER() in code, but legacy
-- rows may still be mixed case:
UPDATE trial_activations
SET tradingview_username = LOWER(tradingview_username)
WHERE tradingview_username IS NOT NULL
  AND tradingview_username <> LOWER(tradingview_username);
```

## Reset trial for user

```sql
DELETE FROM trial_activations WHERE user_id = 3;
DELETE FROM users_subscriptions WHERE user_id = 3 AND is_trial = true;
```
