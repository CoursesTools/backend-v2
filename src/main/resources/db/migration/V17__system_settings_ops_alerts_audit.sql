-- V17 (PLAN-003): admin-editable system settings, ops (system) alert history with Telegram
-- recipients, and an admin audit log.

-- Single-row settings. Constraints double the API validation so a bad value can never be stored.
CREATE TABLE system_settings
(
    id                SMALLINT PRIMARY KEY CHECK (id = 1),
    grace_period_days INTEGER     NOT NULL DEFAULT 7 CHECK (grace_period_days BETWEEN 1 AND 30),
    ops_alerts_paused BOOLEAN     NOT NULL DEFAULT FALSE,
    version           INTEGER     NOT NULL DEFAULT 0,
    updated_at        TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_by        INTEGER REFERENCES users (id)
);
INSERT INTO system_settings (id) VALUES (1);

CREATE TABLE ops_alert_categories
(
    category   VARCHAR(32) PRIMARY KEY,
    enabled    BOOLEAN     NOT NULL DEFAULT TRUE,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_by INTEGER REFERENCES users (id)
);
INSERT INTO ops_alert_categories (category)
VALUES ('PAYMENTS'), ('TV_ACCESS'), ('ALERT_DELIVERY'), ('INFRASTRUCTURE'), ('DIGEST');

CREATE TABLE ops_telegram_recipients
(
    id          INTEGER GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    telegram_id BIGINT      NOT NULL UNIQUE CHECK (telegram_id > 0),
    label       VARCHAR(64),
    created_at  TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_by  INTEGER REFERENCES users (id)
);
INSERT INTO ops_telegram_recipients (telegram_id, label) VALUES (639072017, 'Owner');

-- History of every ops alert, kept whether or not it was delivered to Telegram.
CREATE TABLE ops_alerts
(
    id              BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    category        VARCHAR(32)  NOT NULL,
    severity        VARCHAR(16)  NOT NULL,
    title           VARCHAR(200) NOT NULL,
    body            TEXT         NOT NULL,
    dedup_key       VARCHAR(200),
    telegram_status VARCHAR(32)  NOT NULL,
    telegram_detail VARCHAR(500),
    created_at      TIMESTAMPTZ  NOT NULL DEFAULT CURRENT_TIMESTAMP,
    search_text     TEXT GENERATED ALWAYS AS (lower(title || ' ' || body)) STORED
);
CREATE INDEX ix_ops_alerts_created ON ops_alerts (created_at);
CREATE INDEX ix_ops_alerts_category ON ops_alerts (category, id DESC);
CREATE INDEX ix_ops_alerts_dedup_key ON ops_alerts (dedup_key) WHERE dedup_key IS NOT NULL;

-- Substring search index. Created only when pg_trgm can be installed on this server, so this
-- migration can never block startup; without it search still works via a sequential scan.
DO
$$
    BEGIN
        BEGIN
            CREATE EXTENSION IF NOT EXISTS pg_trgm;
        EXCEPTION
            WHEN OTHERS THEN
                RAISE NOTICE 'pg_trgm unavailable (%), ops alert search uses a sequential scan', SQLERRM;
        END;
        IF EXISTS (SELECT 1 FROM pg_extension WHERE extname = 'pg_trgm') THEN
            EXECUTE 'CREATE INDEX ix_ops_alerts_search_trgm ON ops_alerts USING gin (search_text gin_trgm_ops)';
        END IF;
    END
$$;

-- Edge-trigger state per health check: an alert fires when a check starts failing, repeats as a
-- reminder while it keeps failing, and resolves once.
CREATE TABLE ops_alert_state
(
    check_key        VARCHAR(200) PRIMARY KEY,
    open             BOOLEAN     NOT NULL,
    opened_at        TIMESTAMPTZ,
    last_notified_at TIMESTAMPTZ,
    updated_at       TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE admin_audit_log
(
    id            BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    actor_user_id INTEGER     NOT NULL REFERENCES users (id),
    action        VARCHAR(64) NOT NULL,
    target        VARCHAR(128),
    before_value  JSONB,
    after_value   JSONB,
    created_at    TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);
CREATE INDEX ix_admin_audit_log_created ON admin_audit_log (created_at DESC, id DESC);
