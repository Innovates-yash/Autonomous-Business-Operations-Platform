package com.aisa.audit.domain;

/**
 * The set of security- and change-relevant actions captured by the
 * Audit_Service (Req 23.1): a User authenticating, an Admin changing a role,
 * an approval of a Blueprint, or a deletion (archival) of a Project.
 */
public enum AuditAction {
    AUTHENTICATION,
    ROLE_CHANGE,
    BLUEPRINT_APPROVAL,
    PROJECT_DELETION
}
