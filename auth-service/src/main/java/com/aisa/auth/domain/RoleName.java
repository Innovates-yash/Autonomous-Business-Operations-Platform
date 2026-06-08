package com.aisa.auth.domain;

/**
 * The six platform roles. A {@link User} is assigned exactly one role
 * (Requirement 2.1), with {@link #GUEST} as the default (Requirement 2.2).
 */
public enum RoleName {
    ADMIN,
    ARCHITECT,
    PRODUCT_MANAGER_ROLE,
    DEVELOPER,
    CLIENT,
    GUEST
}
