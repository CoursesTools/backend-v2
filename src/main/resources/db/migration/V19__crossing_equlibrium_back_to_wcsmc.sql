-- V19: Crossing_equlibrium belongs to the WCSMC indicator.
--
-- V8 moved 'crossing_equlibrium' from wcsmc to smctb together with the SMC ToolBox events, but
-- TradingView sends it tagged WCSMC (about 200 alerts/hour, never SMCTB). The alert bot matches on
-- the indicator, so every one was "Alert not found" and its subscribers never received it
-- (verified in prod 2026-11-05: 668 catalog rows, 9 users, 1,156 subscriptions).
--
-- Re-tag in place so alert ids, and with them every existing subscription, are kept. The NOT EXISTS
-- guard skips any row whose wcsmc twin already exists (unique key), so startup can never fail here.
-- ESSENTIALS (wcsmc only) can subscribe to it afterwards, like every other WCSMC event.
UPDATE alerts a
SET indicator  = 'wcsmc',
    updated_at = CURRENT_TIMESTAMP
WHERE a.indicator = 'smctb'
  AND a.event = 'crossing_equlibrium'
  AND NOT EXISTS (SELECT 1
                  FROM alerts w
                  WHERE w.indicator = 'wcsmc'
                    AND w.event = a.event
                    AND w.asset = a.asset
                    AND w.type = a.type
                    AND w.broker = a.broker
                    AND w.tf = a.tf
                    AND w.multi_alert = a.multi_alert);
