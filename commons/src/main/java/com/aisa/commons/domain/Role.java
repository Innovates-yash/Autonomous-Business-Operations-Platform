package com.aisa.commons.domain;

/**
 * The six platform roles. Each User holds exactly one role (Requirement 2.1);
 * {@link #GUEST} is the default when none is specified (Requirement 2.2).
 */
public enum Role {
    ADMIN,
    ARCHITECT,
    PRODUCT_MANAGER,
    DEVELOPER,
    CLIENT,
    GUEST
}
