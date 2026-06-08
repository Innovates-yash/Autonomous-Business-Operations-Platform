-- Project Service initial schema (Requirements 3.1, 3.4, 3.8, 4.x, 7.x).
-- Owns the Project aggregate and its associated entities. Cross-service
-- references (owner_id, initiated_by) are stored by identifier only; there are
-- no foreign keys across service boundaries (database-per-service pattern).

CREATE TABLE project (
    id          BINARY(16)    NOT NULL,
    name        VARCHAR(200)  NOT NULL,
    description VARCHAR(5000) NOT NULL,
    state       VARCHAR(32)   NOT NULL,
    owner_id    BINARY(16)    NOT NULL,
    created_at  DATETIME(6)   NOT NULL,
    updated_at  DATETIME(6)   NOT NULL,
    PRIMARY KEY (id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4;

CREATE INDEX idx_project_owner ON project (owner_id);
CREATE INDEX idx_project_state ON project (state);

CREATE TABLE idea (
    id          BINARY(16)    NOT NULL,
    project_id  BINARY(16)    NOT NULL,
    name        VARCHAR(200)  NOT NULL,
    description VARCHAR(5000) NOT NULL,
    created_at  DATETIME(6)   NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT uq_idea_project UNIQUE (project_id),
    CONSTRAINT fk_idea_project FOREIGN KEY (project_id) REFERENCES project (id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4;

CREATE TABLE requirement (
    id                     BINARY(16)    NOT NULL,
    project_id             BINARY(16)    NOT NULL,
    statement              VARCHAR(2000) NOT NULL,
    type                   VARCHAR(32)   NOT NULL,
    recommended_assumption VARCHAR(2000) NULL,
    created_at             DATETIME(6)   NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT fk_requirement_project FOREIGN KEY (project_id) REFERENCES project (id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4;

CREATE INDEX idx_requirement_project ON requirement (project_id);

CREATE TABLE use_case (
    id          BINARY(16)    NOT NULL,
    project_id  BINARY(16)    NOT NULL,
    title       VARCHAR(200)  NOT NULL,
    description VARCHAR(5000) NOT NULL,
    created_at  DATETIME(6)   NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT fk_use_case_project FOREIGN KEY (project_id) REFERENCES project (id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4;

CREATE INDEX idx_use_case_project ON use_case (project_id);

CREATE TABLE use_case_requirement (
    use_case_id    BINARY(16) NOT NULL,
    requirement_id BINARY(16) NOT NULL,
    PRIMARY KEY (use_case_id, requirement_id),
    CONSTRAINT fk_ucr_use_case FOREIGN KEY (use_case_id) REFERENCES use_case (id),
    CONSTRAINT fk_ucr_requirement FOREIGN KEY (requirement_id) REFERENCES requirement (id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4;

CREATE TABLE clarifying_question (
    id             BINARY(16)    NOT NULL,
    project_id     BINARY(16)    NOT NULL,
    requirement_id BINARY(16)    NULL,
    question       VARCHAR(2000) NOT NULL,
    answer         VARCHAR(5000) NULL,
    created_at     DATETIME(6)   NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT fk_cq_project FOREIGN KEY (project_id) REFERENCES project (id),
    CONSTRAINT fk_cq_requirement FOREIGN KEY (requirement_id) REFERENCES requirement (id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4;

CREATE INDEX idx_cq_project ON clarifying_question (project_id);

CREATE TABLE project_state_transition (
    id           BINARY(16)  NOT NULL,
    project_id   BINARY(16)  NOT NULL,
    from_state   VARCHAR(32) NULL,
    to_state     VARCHAR(32) NOT NULL,
    initiated_by BINARY(16)  NOT NULL,
    occurred_at  DATETIME(6) NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT fk_pst_project FOREIGN KEY (project_id) REFERENCES project (id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4;

CREATE INDEX idx_pst_project ON project_state_transition (project_id);
