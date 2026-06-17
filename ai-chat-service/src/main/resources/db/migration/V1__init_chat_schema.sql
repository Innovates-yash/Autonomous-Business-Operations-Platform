-- AI Chat Service initial schema (Requirements 5.1, 5.2, 5.3, 5.9).
-- Owns the Conversation and ChatMessage entities. Cross-service references
-- (user_id, project_id) are stored by identifier only; no foreign keys across
-- service boundaries (database-per-service pattern).

CREATE TABLE conversation (
    id         BINARY(16)  NOT NULL,
    user_id    BINARY(16)  NOT NULL,
    project_id BINARY(16)  NULL,
    created_at DATETIME(6) NOT NULL,
    updated_at DATETIME(6) NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT uq_conversation_project UNIQUE (project_id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4;

CREATE INDEX idx_conversation_user ON conversation (user_id);

CREATE TABLE chat_message (
    id              BINARY(16)     NOT NULL,
    conversation_id BINARY(16)     NOT NULL,
    role            VARCHAR(16)    NOT NULL,
    content         TEXT           NOT NULL,
    user_id         BINARY(16)     NOT NULL,
    created_at      DATETIME(6)    NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT fk_message_conversation FOREIGN KEY (conversation_id) REFERENCES conversation (id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4;

CREATE INDEX idx_message_conversation ON chat_message (conversation_id);
CREATE INDEX idx_message_user ON chat_message (user_id);
CREATE INDEX idx_message_created_at ON chat_message (conversation_id, created_at DESC);
