package com.aisa.auth.service;

import com.aisa.auth.config.AuthTokenProperties;
import com.aisa.auth.domain.User;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.Date;
import javax.crypto.SecretKey;
import org.springframework.stereotype.Service;

/**
 * Issues short-lived, signed JWT access tokens (Requirements 1.3, 1.4).
 *
 * <p>Tokens are signed with HMAC-SHA256 using the symmetric secret shared with the
 * API Gateway, so a token minted here verifies at the edge. The signing key is
 * derived from {@link AuthTokenProperties#getSecret()} using the same rule as the
 * gateway's {@code JwtVerifier}: a base64 value of at least 32 bytes is decoded,
 * otherwise the raw UTF-8 bytes are used.
 *
 * <p>Each token carries {@code sub} (the user id), {@code role}, {@code email},
 * {@code iss}, {@code iat}, and an {@code exp} set exactly 15 minutes after issuance
 * (Requirement 1.4).
 */
@Service
public class JwtService {

    private final SecretKey signingKey;
    private final String issuer;
    private final Duration accessTokenTtl;

    public JwtService(AuthTokenProperties properties) {
        if (properties.getSecret() == null || properties.getSecret().isBlank()) {
            throw new IllegalStateException(
                    "aisa.auth.token.secret must be configured to sign access tokens");
        }
        this.signingKey = Keys.hmacShaKeyFor(deriveSecretBytes(properties.getSecret()));
        this.issuer = properties.getIssuer();
        this.accessTokenTtl = properties.getAccessTokenTtl();
    }

    /**
     * Signs an access token for the given user.
     *
     * @param user the authenticated user
     * @return the signed token plus its expiry metadata
     */
    public IssuedAccessToken issue(User user) {
        Instant now = Instant.now();
        Instant expiresAt = now.plus(accessTokenTtl);

        var builder = Jwts.builder()
                .subject(String.valueOf(user.getId()))
                .claim("role", user.getRole().getName().name())
                .claim("email", user.getEmail())
                .issuedAt(Date.from(now))
                .expiration(Date.from(expiresAt))
                .signWith(signingKey);

        if (issuer != null && !issuer.isBlank()) {
            builder.issuer(issuer);
        }

        return new IssuedAccessToken(builder.compact(), expiresAt, accessTokenTtl.getSeconds());
    }

    /**
     * Derives signing-key bytes, matching the gateway verifier: prefer a base64 value
     * of sufficient length, otherwise fall back to the raw UTF-8 bytes.
     */
    private static byte[] deriveSecretBytes(String secret) {
        try {
            byte[] decoded = Base64.getDecoder().decode(secret);
            if (decoded.length >= 32) {
                return decoded;
            }
        } catch (IllegalArgumentException ignored) {
            // not base64 — use the raw bytes below
        }
        return secret.getBytes(StandardCharsets.UTF_8);
    }
}
