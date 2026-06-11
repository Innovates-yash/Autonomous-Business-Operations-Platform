package com.aisa.auth.web.dto;

import jakarta.validation.constraints.NotNull;

/**
 * Request body for the admin role-assignment endpoint
 * {@code POST /api/admin/users/{userId}/role} (Requirements 2.1, 2.7, 2.13).
 *
 * @param role the name of the role to assign (must be a valid RoleName enum value)
 */
public record RoleAssignmentRequest(
        @NotNull(message = "role is required")
        String role
) {
}
