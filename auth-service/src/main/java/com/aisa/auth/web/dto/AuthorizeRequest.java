package com.aisa.auth.web.dto;

import jakarta.validation.constraints.NotNull;

/**
 * Request payload for the authorization decision endpoint (POST /api/auth/authorize).
 * The caller specifies the user and the permission to evaluate.
 *
 * @param userId     the identifier of the user requesting the action
 * @param permission the permission required for the action
 */
public record AuthorizeRequest(
        @NotNull(message = "userId is required")
        Long userId,

        @NotNull(message = "permission is required")
        String permission
) {
}
