ALTER TABLE users_alerts
    DROP CONSTRAINT users_alerts_pkey,
    DROP COLUMN id,
    ADD PRIMARY KEY (user_id, alert_id);
