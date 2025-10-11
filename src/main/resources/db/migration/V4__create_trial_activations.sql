CREATE TABLE trial_activations
(
    user_id              INT          NOT NULL,
    tradingview_username VARCHAR(255) NOT NULL UNIQUE,
    activation_date      TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (user_id),
    CONSTRAINT fk_trial_activations_user FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE CASCADE
);

CREATE INDEX idx_trial_activations_tradingview_username ON trial_activations (tradingview_username);

-- Вставка существующих пользователей с подпиской COURSESTOOLSPRO
INSERT INTO trial_activations (user_id, tradingview_username, activation_date)
SELECT DISTINCT
    us.user_id,
    usoc.trading_view_name,
    us.created_at
FROM users_subscriptions us
JOIN subscription_plans sp ON us.plan_id = sp.id
JOIN subscription_types st ON sp.subscription_type_id = st.id
JOIN user_socials usoc ON us.user_id = usoc.user_id
WHERE st.name = 'COURSESTOOLSPRO'
ON CONFLICT (user_id) DO NOTHING;
