-- =============================================================================
-- Audit Service - append-only audit store (Req 23.1, 23.7)
--
-- This migration creates the write-once `audit_event` table. Audit records are
-- INSERT-only: once written they may never be modified or deleted by any role
-- (Req 23.7). Immutability is enforced at the data layer in two complementary
-- ways:
--   1. Privilege revocation - the application database role is granted only
--      INSERT and SELECT; UPDATE and DELETE grants are revoked (see below).
--   2. Hard enforcement - BEFORE UPDATE / BEFORE DELETE triggers raise an error
--      so the table stays append-only even if a privileged role attempts a
--      mutation (e.g. the schema owner).
-- =============================================================================

CREATE TABLE audit_event (
    id             BIGINT       NOT NULL AUTO_INCREMENT,
    user_id        VARCHAR(254) NOT NULL,
    action         VARCHAR(64)  NOT NULL,
    target_id      VARCHAR(255) NOT NULL,
    -- UTC timestamp with millisecond precision (Req 23.1).
    occurred_at    DATETIME(3)  NOT NULL,
    correlation_id VARCHAR(64)  NULL,
    PRIMARY KEY (id)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_unicode_ci
  COMMENT = 'Append-only, write-once audit log. INSERT/SELECT only - no UPDATE/DELETE (Req 23.7).';

-- Query support for Admin-only filtering by user, action, and time range
-- (Req 23.4); also accelerates 365-day retention scans (Req 23.3).
CREATE INDEX idx_audit_event_user_id ON audit_event (user_id);
CREATE INDEX idx_audit_event_action ON audit_event (action);
CREATE INDEX idx_audit_event_occurred_at ON audit_event (occurred_at);

-- -----------------------------------------------------------------------------
-- Write-once enforcement via triggers. Single-statement bodies (no BEGIN/END)
-- keep these portable through Flyway's MySQL statement splitter.
-- -----------------------------------------------------------------------------
CREATE TRIGGER trg_audit_event_no_update
    BEFORE UPDATE ON audit_event
    FOR EACH ROW
    SIGNAL SQLSTATE '45000'
        SET MESSAGE_TEXT = 'audit_event is append-only: UPDATE is not permitted (Req 23.7)';

CREATE TRIGGER trg_audit_event_no_delete
    BEFORE DELETE ON audit_event
    FOR EACH ROW
    SIGNAL SQLSTATE '45000'
        SET MESSAGE_TEXT = 'audit_event is append-only: DELETE is not permitted (Req 23.7)';

-- -----------------------------------------------------------------------------
-- Privilege model (Req 23.7).
--
-- The application role must hold ONLY INSERT and SELECT on this table; UPDATE
-- and DELETE grants must be revoked so the data layer rejects mutation attempts.
-- Grant management is environment-specific (the application username differs
-- per environment and is provisioned outside this migration), so the intended
-- grants are documented here for the DBA / provisioning automation to apply:
--
--   GRANT INSERT, SELECT ON aisa.audit_event TO '<app_user>'@'%';
--   REVOKE UPDATE, DELETE ON aisa.audit_event FROM '<app_user>'@'%';
--
-- The triggers above provide defense-in-depth so immutability holds even if the
-- grant model is misconfigured.
-- -----------------------------------------------------------------------------
