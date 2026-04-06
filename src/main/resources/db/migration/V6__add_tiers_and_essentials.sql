-- 1. Add tier column to subscription_plans (all existing plans default to PRO)
ALTER TABLE subscription_plans
ADD COLUMN tier VARCHAR(32) NOT NULL DEFAULT 'PRO';

-- 2. Rename subscription type from COURSESTOOLSPRO to COURSESTOOLS (product family)
UPDATE subscription_types SET name = 'COURSESTOOLS', display_name = 'CoursesTools'
WHERE name = 'COURSESTOOLSPRO';

-- 3. Insert Essentials plans
INSERT INTO subscription_plans (subscription_type_id, name, display_name, price, duration_days, discount_multiplier, tier)
VALUES
    ((SELECT id FROM subscription_types WHERE name = 'COURSESTOOLS'), 'MONTH', 'Essentials Month', 1490, 30, 1, 'ESSENTIALS'),
    ((SELECT id FROM subscription_types WHERE name = 'COURSESTOOLS'), 'YEAR', 'Essentials Year', 11950, 365, 1, 'ESSENTIALS'),
    ((SELECT id FROM subscription_types WHERE name = 'COURSESTOOLS'), 'LIFETIME', 'Essentials Lifetime', 19930, 9999, 1, 'ESSENTIALS');

-- 4. Create tier_indicator_permissions table (allowlist model)
-- No rows for a tier = unrestricted (PRO). Rows present = restricted to listed indicators only.
CREATE TABLE tier_indicator_permissions (
    id INTEGER GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    tier VARCHAR(32) NOT NULL,
    indicator VARCHAR(32) NOT NULL,
    subscription_type_id INTEGER NOT NULL REFERENCES subscription_types(id),
    created_at TIMESTAMPTZ DEFAULT CURRENT_TIMESTAMP,
    UNIQUE(tier, indicator, subscription_type_id)
);

-- 5. Essentials can only use WCSMC indicator
INSERT INTO tier_indicator_permissions (tier, indicator, subscription_type_id)
VALUES ('ESSENTIALS', 'WCSMC', (SELECT id FROM subscription_types WHERE name = 'COURSESTOOLS'));

-- 6. Add promo code scoping columns
ALTER TABLE codes
ADD COLUMN subscription_type_id INTEGER REFERENCES subscription_types(id),
ADD COLUMN tier VARCHAR(32);
