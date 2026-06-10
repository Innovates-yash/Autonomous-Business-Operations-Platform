package com.aisa.auth.config;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Token-strategy configuration for the Auth Service.
 *
 * <p>Access tokens are short-lived signed JWTs (Requirements 1.3, 1.4) and refresh
 * tokens are opaque, hashed, single-use values with a longer lifetime (Requirements
 * 1.5, 1.6). The {@link #secret} is the symmetric HMAC key shared with the API
 * Gateway's {@code JwtVerifier}; tokens signed here therefore verify at the edge.
 *
 * <p>The defaults encode the requirement-mandated lifetimes: a 15-minute access token
 * (Requirement 1.4) and a 7-day refresh token (Requirement 1.5).
 */
@ConfigurationProperties(prefix = "aisa.auth.token")
public class AuthTokenProperties {

    /**
     * Symmetric HMAC signing secret shared with the API Gateway. Accepts either a
     * base64-encoded key or raw text; must yield at least 256 bits (32 bytes) for HS256.
     */
    private String secret;

    /** Token issuer claim ({@code iss}); the gateway may require it when configured. */
    private String issuer = "aisa-auth";

    /** Access-token lifetime; fixed at 15 minutes by Requirement 1.4. */
    private Duration accessTokenTtl = Duration.ofMinutes(15);

    /** Refresh-token lifetime; fixed at 7 days by Requirement 1.5. */
    private Duration refreshTokenTtl = Duration.ofDays(7);

    public String getSecret() {
        return secret;
    }

    public void setSecret(String secret) {
        this.secret = secret;
    }

    public String getIssuer() {
        return issuer;
    }

    public void setIssuer(String issuer) {
        this.issuer = issuer;
    }

    public Duration getAccessTokenTtl() {
        return accessTokenTtl;
    }

    public void setAccessTokenTtl(Duration accessTokenTtl) {
        this.accessTokenTtl = accessTokenTtl;
    }

    public Duration getRefreshTokenTtl() {
        return refreshTokenTtl;
    }

    public void setRefreshTokenTtl(Duration refreshTokenTtl) {
        this.refreshTokenTtl = refreshTokenTtl;
    }
}
