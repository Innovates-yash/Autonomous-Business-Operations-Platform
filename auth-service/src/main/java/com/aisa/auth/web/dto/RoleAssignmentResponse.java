package com.aisa.auth.web.dto;

import com.aisa.auth.domain.User;

/**
 * Response body returned after a successful role assignment
 * (Requirements 2.1, 2.13).
 *
 * @param userId the target user's id
 * @param email the target user's email
 * @param role the newly assigned role name
 */
public record RoleAssignmentResponse(
        Long userId,
        String email,
        String role
) {

    public static RoleAssignmentResponse from(User user) {
        return new RoleAssignmentResponse(
                user.getId(),
                user.getEmail(),
                user.getRole().getName().name());
    }
}
