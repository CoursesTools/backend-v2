CREATE INDEX idx_users_subscriptions_non_terminated_expired_at
    ON users_subscriptions (expired_at)
    WHERE is_trial = FALSE AND status <> 'TERMINATED';
