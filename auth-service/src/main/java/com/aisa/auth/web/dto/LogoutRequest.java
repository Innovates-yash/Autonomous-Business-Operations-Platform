package com.aisa.auth.web.dto;

import jakarta.validation.constraints.NotBlank;

/**
 * Logout payload identifying the session to invalidate by its refresh token
 * (Requirement 1.10).
 *
 * @param refreshToken the opaque refresh token whose session should be revoked
 */
public record LogoutRequest(
        @NotBlank(message = "Refresh token is required") String refreshToken) {
}
