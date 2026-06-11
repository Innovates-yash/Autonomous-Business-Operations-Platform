package com.aisa.auth.web.dto;

import jakarta.validation.constraints.NotBlank;

/**
 * Request body for the OAuth2 authorization-code callback endpoint (Requirement 1.8).
 * The client provides the provider key, the authorization code received from the
 * provider's redirect, and the redirect URI originally used in the authorization request.
 *
 * @param provider    the OAuth2 provider key (e.g. "google", "github")
 * @param code        the authorization code from the provider's callback
 * @param redirectUri the redirect URI used in the original authorization request
 */
public record OAuth2CallbackRequest(
        @NotBlank(message = "Provider is required")
        String provider,

        @NotBlank(message = "Authorization code is required")
        String code,

        @NotBlank(message = "Redirect URI is required")
        String redirectUri) {
}
