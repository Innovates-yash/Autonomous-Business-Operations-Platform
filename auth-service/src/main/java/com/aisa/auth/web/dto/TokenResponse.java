package com.aisa.auth.web.dto;

/**
 * Token issuance result returned by login and refresh (Requirements 1.3, 1.6).
 *
 * <p>The access token is a signed JWT that expires 15 minutes after issuance
 * (Requirement 1.4); the refresh token is the opaque value the client presents to
 * obtain a new pair, valid for 7 days (Requirement 1.5).
 *
 * @param accessToken      the signed JWT access token
 * @param refreshToken     the opaque refresh token
 * @param tokenType        the access-token scheme; always {@code Bearer}
 * @param expiresInSeconds access-token lifetime in seconds (900 for the 15-minute TTL)
 */
public record TokenResponse(
        String accessToken,
        String refreshToken,
        String tokenType,
        long expiresInSeconds) {
}
