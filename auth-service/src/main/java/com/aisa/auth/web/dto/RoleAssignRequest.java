package com.aisa.auth.web.dto;

import jakarta.validation.constraints.NotNull;

/**
 * Request body for the admin role-assignment endpoint
 * {@code POST /api/admin/roles/assign} (Requirements 2.1, 2.7, 2.13, 2.14).
 *
 * <p>Accepts both the target user's id and the role to assign in a single body,
 * which is the canonical form specified by the design for bulk admin operations.
 *
 * @param userId the id of the user whose role is being changed
 * @param targetRole the name of the role to assign (must be a valid RoleName enum value)
 */
public record RoleAssignRequest(
        @NotNull(message = "userId is required")
        Long userId,
        @NotNull(message = "targetRole is required")
        String targetRole
) {
}
