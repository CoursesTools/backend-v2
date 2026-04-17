ALTER TABLE trading_view_retry_jobs
    ADD COLUMN force_retry_count INTEGER NOT NULL DEFAULT 0;
