package com.aisa.auth.service;

import java.time.Instant;

/**
 * A freshly minted JWT access token and its expiry metadata (Requirements 1.3, 1.4).
 *
 * @param token            the signed compact JWT
 * @param expiresAt        the absolute expiry instant
 * @param expiresInSeconds the lifetime in seconds (900 for the 15-minute TTL)
 */
public record IssuedAccessToken(String token, Instant expiresAt, long expiresInSeconds) {
}
