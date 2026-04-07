-- V7: Add SMCTB indicator alerts, confirm PRO tier on existing plans, confirm universal promo codes
--
-- 1. For each existing WCSMC alert whose event belongs to the SMC ToolBox event set,
--    insert a mirror row with indicator = 'smctb'.
--    ON CONFLICT DO NOTHING makes this idempotent / safe to re-run.
--
-- 2. Belt-and-suspenders: set tier = 'PRO' on any subscription_plans row that somehow
--    missed the V6 DEFAULT 'PRO'. Should be a no-op on a clean V6 database.
--
-- 3. Belt-and-suspenders: ensure all existing promo codes remain universal (tier = NULL).
--    V6 added the column with no DEFAULT, so existing rows are already NULL.
--    This guard covers any manual data that may have slipped in.

-- ─────────────────────────────────────────────────────────────────────────────
-- 1. Insert SMCTB alert variants
-- ─────────────────────────────────────────────────────────────────────────────
INSERT INTO alerts (type, broker, tf, event, asset, indicator, multi_alert)
SELECT type, broker, tf, event, asset, 'smctb', multi_alert
FROM alerts
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
  )
ON CONFLICT (asset, event, type, broker, tf, indicator, multi_alert) DO NOTHING;

-- ─────────────────────────────────────────────────────────────────────────────
-- 2. Ensure all existing subscription plans are marked PRO
--    (V6 added tier NOT NULL DEFAULT 'PRO' — this is a safety guard only)
-- ─────────────────────────────────────────────────────────────────────────────
UPDATE subscription_plans
SET tier = 'PRO'
WHERE tier IS NULL OR tier = '';

-- ─────────────────────────────────────────────────────────────────────────────
-- 3. Ensure all existing promo codes are universal (tier = NULL)
--    (V6 added the nullable tier column with no default — existing rows are
--    already NULL, but this guard prevents any stale manual data from leaking)
-- ─────────────────────────────────────────────────────────────────────────────
UPDATE codes
SET tier = NULL
WHERE tier IS NOT NULL;
