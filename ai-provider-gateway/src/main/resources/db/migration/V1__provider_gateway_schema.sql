-- AI Provider Gateway schema (Requirement 20).
-- Owns provider configuration, the active-provider selection history, and the
-- per-request usage records retained for >= 90 days (Req 20.8).

CREATE TABLE provider_config (
    id                      VARCHAR(36)  NOT NULL,
    provider                VARCHAR(32)  NOT NULL,
    display_name            VARCHAR(100) NOT NULL,
    model                   VARCHAR(100) NOT NULL,
    configured              BIT(1)       NOT NULL DEFAULT b'0',
    request_timeout_seconds INT          NOT NULL DEFAULT 30,
    fallback_priority       INT          NULL,
    created_at              DATETIME(6)  NOT NULL,
    updated_at              DATETIME(6)  NOT NULL,
    CONSTRAINT pk_provider_config PRIMARY KEY (id),
    CONSTRAINT uq_provider_config_provider UNIQUE (provider),
    CONSTRAINT ck_provider_config_timeout CHECK (request_timeout_seconds BETWEEN 1 AND 120)
) ENGINE = InnoDB;

CREATE TABLE provider_selection (
    id                VARCHAR(36) NOT NULL,
    selected_provider VARCHAR(32) NOT NULL,
    selected_by       VARCHAR(36) NOT NULL,
    selected_at       DATETIME(6) NOT NULL,
    CONSTRAINT pk_provider_selection PRIMARY KEY (id)
) ENGINE = InnoDB;

CREATE INDEX idx_selection_selected_at ON provider_selection (selected_at);

CREATE TABLE provider_usage_record (
    id             VARCHAR(36) NOT NULL,
    provider       VARCHAR(32) NOT NULL,
    operation      VARCHAR(16) NOT NULL,
    correlation_id VARCHAR(64) NULL,
    success        BIT(1)      NOT NULL,
    served_at      DATETIME(6) NOT NULL,
    CONSTRAINT pk_provider_usage_record PRIMARY KEY (id)
) ENGINE = InnoDB;

CREATE INDEX idx_usage_served_at ON provider_usage_record (served_at);
CREATE INDEX idx_usage_provider  ON provider_usage_record (provider);
