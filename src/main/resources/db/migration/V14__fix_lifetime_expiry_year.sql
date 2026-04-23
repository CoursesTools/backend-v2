-- Fix lifetime subscriptions: TradingView rejects year 9999 as too far in the future.
-- Change sentinel from 9999-12-31 to 2100-12-31.

UPDATE users_subscriptions
SET expired_at = '2100-12-31 23:59:59'
WHERE expired_at = '9999-12-31 23:59:59';

-- Patch ACTIVATE retry job payloads that carry the old sentinel.
UPDATE trading_view_retry_jobs
SET payload = jsonb_set(
        jsonb_set(
            payload::jsonb,
            '{expiration}',
            to_jsonb(replace((payload::jsonb ->> 'expiration'), '9999', '2100'))
        ),
        '{isLifetime}',
        'true'
    )
WHERE type = 'ACTIVATE'
  AND payload::jsonb ->> 'expiration' LIKE '9999%';

-- Patch RENAME retry job payloads similarly.
UPDATE trading_view_retry_jobs
SET payload = jsonb_set(
        jsonb_set(
            payload::jsonb,
            '{expiration}',
            to_jsonb(replace((payload::jsonb ->> 'expiration'), '9999', '2100'))
        ),
        '{isLifetime}',
        'true'
    )
WHERE type = 'RENAME'
  AND payload::jsonb ->> 'expiration' LIKE '9999%';
