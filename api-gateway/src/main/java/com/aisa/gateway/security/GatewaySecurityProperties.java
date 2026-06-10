package com.aisa.gateway.security;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.List;

/**
 * Edge security configuration for the API Gateway.
 *
 * <p>Supports Requirements 25.1 and 25.2: all client-server traffic must travel over
 * encrypted transport, and connections arriving over an unencrypted channel must be
 * rejected without processing the request payload.
 *
 * <p>Also supports Requirements 27.5 and 27.6 (correlation propagation) and edge JWT
 * validation: access tokens are verified at the gateway and the authenticated principal
 * is forwarded downstream, while public routes (authentication endpoints, health probes)
 * are allowed through without a token.
 *
 * <p>Enforcement is configurable per environment so that local development can run over
 * plaintext HTTP while staging and production enforce TLS. When TLS is terminated by an
 * upstream load balancer or ingress rather than the gateway itself, the gateway trusts
 * the forwarded protocol headers it sets (for example {@code X-Forwarded-Proto}).
 */
@ConfigurationProperties(prefix = "aisa.gateway.security")
public class GatewaySecurityProperties {

    /**
     * When {@code true}, the gateway rejects any request that did not arrive over an
     * encrypted transport channel. Defaults to {@code false} for local development.
     */
    private boolean requireSecureTransport = false;

    /**
     * Ant-style path patterns that bypass JWT validation. By default these are the
     * authentication endpoints (login/register/refresh/OAuth2 callback) and the actuator
     * health/info/metrics probes, which must be reachable by unauthenticated clients and
     * by infrastructure health checks.
     */
    private List<String> publicPaths = List.of(
            "/api/auth/**",
            "/actuator/health/**",
            "/actuator/info",
            "/actuator/prometheus");

    /** Edge JWT access-token validation settings. */
    private final Jwt jwt = new Jwt();

    /** Edge rate-limiting settings (Req 25.4, 25.5). */
    private final RateLimit rateLimit = new RateLimit();

    public boolean isRequireSecureTransport() {
        return requireSecureTransport;
    }

    public void setRequireSecureTransport(boolean requireSecureTransport) {
        this.requireSecureTransport = requireSecureTransport;
    }

    public List<String> getPublicPaths() {
        return publicPaths;
    }

    public void setPublicPaths(List<String> publicPaths) {
        this.publicPaths = publicPaths;
    }

    public Jwt getJwt() {
        return jwt;
    }

    public RateLimit getRateLimit() {
        return rateLimit;
    }

    /**
     * Edge JWT validation configuration. The gateway verifies the signature and expiry of
     * access tokens; downstream services re-verify per their own authority (defense in
     * depth). A symmetric {@code secret} or an RSA {@code publicKey} (PEM) may be supplied;
     * the public key takes precedence when both are present.
     */
    public static class Jwt {

        /**
         * When {@code true}, the gateway validates access tokens on protected routes.
         * Defaults to {@code true}; disable only for environments where edge validation
         * is intentionally delegated (for example a local dev profile without auth).
         */
        private boolean enabled = true;

        /**
         * Symmetric HMAC signing secret shared with the Auth Service. Used when no RSA
         * {@code publicKey} is configured. Must be at least 256 bits (32 bytes) for HS256.
         */
        private String secret;

        /**
         * RSA public key in PEM form (with or without the {@code -----BEGIN PUBLIC KEY-----}
         * armor) used to verify asymmetrically signed tokens. Takes precedence over
         * {@code secret} when set.
         */
        private String publicKey;

        /** Expected token issuer; when set, tokens with a different issuer are rejected. */
        private String issuer;

        /** Allowed clock skew, in seconds, applied to expiry/not-before checks. */
        private long clockSkewSeconds = 60;

        public boolean isEnabled() {
            return enabled;
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }

        public String getSecret() {
            return secret;
        }

        public void setSecret(String secret) {
            this.secret = secret;
        }

        public String getPublicKey() {
            return publicKey;
        }

        public void setPublicKey(String publicKey) {
            this.publicKey = publicKey;
        }

        public String getIssuer() {
            return issuer;
        }

        public void setIssuer(String issuer) {
            this.issuer = issuer;
        }

        public long getClockSkewSeconds() {
            return clockSkewSeconds;
        }

        public void setClockSkewSeconds(long clockSkewSeconds) {
            this.clockSkewSeconds = clockSkewSeconds;
        }
    }

    /**
     * Edge rate-limiting configuration (Requirements 25.4, 25.5).
     *
     * <p>The gateway enforces a fixed-window quota per client: once a client exceeds
     * {@code limit} requests within a {@code windowSeconds} window, further requests are
     * rejected for the remainder of that window with HTTP 429 and a {@code Retry-After}
     * header (25.4). When the window elapses the client's counter resets and requests are
     * accepted again (25.5). Counters live in Redis keyed by the authenticated principal
     * (falling back to client IP), so the limit is enforced consistently across stateless
     * gateway instances.
     */
    public static class RateLimit {

        /**
         * When {@code true}, the gateway enforces the per-client request quota. Defaults to
         * {@code true}; disable only where edge rate limiting is intentionally delegated.
         */
        private boolean enabled = true;

        /** Maximum number of requests permitted per client within one window (Req 25.4). */
        private int limit = 100;

        /** Length of the fixed rate-limit window in seconds (Req 25.4, 25.5). */
        private long windowSeconds = 60;

        public boolean isEnabled() {
            return enabled;
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }

        public int getLimit() {
            return limit;
        }

        public void setLimit(int limit) {
            this.limit = limit;
        }

        public long getWindowSeconds() {
            return windowSeconds;
        }

        public void setWindowSeconds(long windowSeconds) {
            this.windowSeconds = windowSeconds;
        }
    }
}
