package com.aisa.gateway.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jws;
import io.jsonwebtoken.JwtParser;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.security.Keys;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.security.Key;
import java.security.KeyFactory;
import java.security.spec.X509EncodedKeySpec;
import java.util.Base64;

/**
 * Verifies the signature and expiry of access tokens presented at the edge.
 *
 * <p>Supports the API Gateway responsibility of validating JWT access tokens before
 * routing to downstream services. The verification key is resolved from
 * {@link GatewaySecurityProperties.Jwt}: an RSA public key (PEM) takes precedence and
 * enables asymmetric verification per the design's token strategy; otherwise a shared
 * HMAC secret is used. The expected issuer and a configurable clock skew are applied when
 * present.
 *
 * <p>Verification is purely a function of the configured key and the token; it performs
 * no I/O, so it is safe to invoke inline from a reactive filter.
 */
@Component
public class JwtVerifier {

    private static final Logger log = LoggerFactory.getLogger(JwtVerifier.class);

    private final GatewaySecurityProperties.Jwt config;
    private final JwtParser parser;

    public JwtVerifier(GatewaySecurityProperties properties) {
        this.config = properties.getJwt();
        this.parser = buildParser(this.config);
    }

    /**
     * Whether a verification key is configured. When {@code false}, the gateway cannot
     * validate tokens and (when validation is enabled) fails closed on protected routes.
     */
    public boolean isConfigured() {
        return parser != null;
    }

    /**
     * Verifies the token and returns its claims.
     *
     * @throws JwtException                  if the signature is invalid, the token is
     *                                       expired/not-yet-valid, the issuer mismatches,
     *                                       or the token is otherwise malformed
     * @throws IllegalStateException         if no verification key is configured
     */
    public Claims verify(String token) {
        if (parser == null) {
            throw new IllegalStateException("JWT verification key is not configured");
        }
        Jws<Claims> jws = parser.parseSignedClaims(token);
        return jws.getPayload();
    }

    private JwtParser buildParser(GatewaySecurityProperties.Jwt jwt) {
        Key key = resolveKey(jwt);
        if (key == null) {
            return null;
        }
        // The builder API distinguishes asymmetric (public key) from symmetric (secret key)
        // verification, so select the matching overload explicitly.
        var parserBuilder = Jwts.parser();
        if (key instanceof java.security.PublicKey publicKey) {
            parserBuilder.verifyWith(publicKey);
        } else if (key instanceof javax.crypto.SecretKey secretKey) {
            parserBuilder.verifyWith(secretKey);
        } else {
            return null;
        }
        if (jwt.getIssuer() != null && !jwt.getIssuer().isBlank()) {
            parserBuilder.requireIssuer(jwt.getIssuer());
        }
        parserBuilder.clockSkewSeconds(Math.max(0, jwt.getClockSkewSeconds()));
        return parserBuilder.build();
    }

    private Key resolveKey(GatewaySecurityProperties.Jwt jwt) {
        if (jwt.getPublicKey() != null && !jwt.getPublicKey().isBlank()) {
            try {
                return parseRsaPublicKey(jwt.getPublicKey());
            } catch (Exception e) {
                log.error("Failed to parse configured RSA public key for JWT verification", e);
                return null;
            }
        }
        if (jwt.getSecret() != null && !jwt.getSecret().isBlank()) {
            byte[] secretBytes = decodeSecret(jwt.getSecret());
            return Keys.hmacShaKeyFor(secretBytes);
        }
        return null;
    }

    private byte[] decodeSecret(String secret) {
        // Accept either a base64-encoded secret or raw text; fall back to raw bytes.
        try {
            byte[] decoded = Base64.getDecoder().decode(secret);
            if (decoded.length >= 32) {
                return decoded;
            }
        } catch (IllegalArgumentException ignored) {
            // not base64 — use raw bytes
        }
        return secret.getBytes(StandardCharsets.UTF_8);
    }

    private java.security.PublicKey parseRsaPublicKey(String pem) throws Exception {
        String normalized = pem
                .replace("-----BEGIN PUBLIC KEY-----", "")
                .replace("-----END PUBLIC KEY-----", "")
                .replaceAll("\\s", "");
        byte[] der = Base64.getDecoder().decode(normalized);
        X509EncodedKeySpec spec = new X509EncodedKeySpec(der);
        return KeyFactory.getInstance("RSA").generatePublic(spec);
    }
}
