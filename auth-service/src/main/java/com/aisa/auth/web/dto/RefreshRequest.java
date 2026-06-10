package com.aisa.auth.web.dto;

import jakarta.validation.constraints.NotBlank;

/**
 * Refresh payload carrying the opaque refresh token to be rotated
 * (Requirements 1.6, 1.7).
 *
 * @param refreshToken the opaque refresh token previously issued to the client
 */
public record RefreshRequest(
        @NotBlank(message = "Refresh token is required") String refreshToken) {
}
