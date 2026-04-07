-- V8: Fix SMCTB alert migration
--
-- V7 incorrectly INSERTED duplicate rows. The correct behavior is to UPDATE
-- existing WCSMC alerts to SMCTB for the 9 SMC ToolBox events.
-- Total alert count must stay at ~20540, not grow.
--
-- This migration:
-- 1. Clears all user alert subscriptions (users must resubscribe)
-- 2. Deletes the duplicate SMCTB rows that V7 created
-- 3. Updates existing WCSMC alerts to SMCTB for the correct events

-- ─────────────────────────────────────────────────────────────────────────────
-- 1. Clear all user alert subscriptions
-- ─────────────────────────────────────────────────────────────────────────────
DELETE FROM users_alerts;

-- ─────────────────────────────────────────────────────────────────────────────
-- 2. Delete duplicate SMCTB alerts created by V7
-- ─────────────────────────────────────────────────────────────────────────────
DELETE FROM alerts WHERE indicator = 'smctb';

-- ─────────────────────────────────────────────────────────────────────────────
-- 3. Update existing WCSMC alerts to SMCTB for SMC ToolBox events
-- ─────────────────────────────────────────────────────────────────────────────
UPDATE alerts
SET indicator = 'smctb'
WHERE indicator = 'wcsmc'
  AND event IN (
    'pdh',
    'pdl',
    'ifc',
    'engulfing_candle',
    'scob_long',
    'scob_short',
    'crossing_equlibrium',
    'scob_long_with_ib',
    'scob_short_with_ib'
  );
