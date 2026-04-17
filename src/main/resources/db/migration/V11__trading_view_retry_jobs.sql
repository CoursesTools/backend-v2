CREATE TABLE trading_view_retry_jobs (
    id                SERIAL PRIMARY KEY,
    user_id           INTEGER NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    type              VARCHAR(32) NOT NULL,
    status            VARCHAR(16) NOT NULL,
    payload           JSONB       NOT NULL,
    next_attempt_at   TIMESTAMP   NOT NULL,
    attempts          INTEGER     NOT NULL DEFAULT 0,
    last_error        VARCHAR(2048),
    first_enqueued_at TIMESTAMP   NOT NULL,
    created_at        TIMESTAMP   NOT NULL DEFAULT now(),
    updated_at        TIMESTAMP   NOT NULL DEFAULT now()
);

CREATE INDEX idx_tv_retry_due
    ON trading_view_retry_jobs (status, next_attempt_at);

-- Only one PENDING job per (user, type): a newer enqueue replaces the stale
-- one rather than stacking conflicting activations.
CREATE UNIQUE INDEX idx_tv_retry_pending_dedup
    ON trading_view_retry_jobs (user_id, type)
    WHERE status = 'PENDING';
