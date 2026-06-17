-- Failed work item table for Kafka re-queue (Requirements 26.5, 26.6).
-- When Kafka is unavailable, the KafkaSubmissionService persists the message here.
-- The FailedWorkRequeueScheduler polls PENDING items and re-publishes them within 30s.

CREATE TABLE failed_work_item (
    id              BINARY(16)     NOT NULL,
    topic           VARCHAR(255)   NOT NULL,
    message_key     VARCHAR(255)   NULL,
    payload         LONGTEXT       NOT NULL,
    payload_type    VARCHAR(512)   NOT NULL,
    status          VARCHAR(32)    NOT NULL DEFAULT 'PENDING',
    retry_count     INT            NOT NULL DEFAULT 0,
    max_retries     INT            NOT NULL DEFAULT 5,
    error_message   VARCHAR(2000)  NULL,
    created_at      DATETIME(6)    NOT NULL,
    updated_at      DATETIME(6)    NOT NULL,
    last_attempt_at DATETIME(6)    NULL,
    PRIMARY KEY (id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4;

CREATE INDEX idx_failed_work_item_status ON failed_work_item (status, created_at);
