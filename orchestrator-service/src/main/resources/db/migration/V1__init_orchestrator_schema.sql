-- Orchestrator Service initial schema (Requirements 6.1, 6.2, 6.9).
-- Owns the GenerationRun aggregate and its associated AgentInvocation and AgentOutput
-- entities. Cross-service references (project_id) are stored by identifier only; there
-- are no foreign keys across service boundaries (database-per-service pattern).

CREATE TABLE generation_run (
    id          BINARY(16)   NOT NULL,
    project_id  BINARY(16)   NOT NULL,
    status      VARCHAR(32)  NOT NULL,
    created_at  DATETIME(6)  NOT NULL,
    updated_at  DATETIME(6)  NOT NULL,
    PRIMARY KEY (id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4;

CREATE INDEX idx_generation_run_project ON generation_run (project_id);
CREATE INDEX idx_generation_run_status ON generation_run (status);

CREATE TABLE agent_invocation (
    id                BINARY(16)     NOT NULL,
    generation_run_id BINARY(16)     NOT NULL,
    agent_type        VARCHAR(32)    NOT NULL,
    status            VARCHAR(32)    NOT NULL,
    attempt_count     INT            NOT NULL DEFAULT 0,
    started_at        DATETIME(6)    NULL,
    completed_at      DATETIME(6)    NULL,
    error_message     VARCHAR(2000)  NULL,
    created_at        DATETIME(6)    NOT NULL,
    updated_at        DATETIME(6)    NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT fk_invocation_run FOREIGN KEY (generation_run_id) REFERENCES generation_run (id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4;

CREATE INDEX idx_invocation_run ON agent_invocation (generation_run_id);
CREATE INDEX idx_invocation_agent_type ON agent_invocation (agent_type);

CREATE TABLE agent_output (
    id            BINARY(16)   NOT NULL,
    invocation_id BINARY(16)   NOT NULL,
    content       LONGTEXT     NOT NULL,
    produced_at   DATETIME(6)  NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT uq_output_invocation UNIQUE (invocation_id),
    CONSTRAINT fk_output_invocation FOREIGN KEY (invocation_id) REFERENCES agent_invocation (id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4;
