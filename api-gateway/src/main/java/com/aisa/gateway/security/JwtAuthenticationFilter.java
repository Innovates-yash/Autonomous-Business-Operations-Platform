package com.aisa.gateway.security;

import com.aisa.commons.error.ErrorCodes;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.Ordered;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebFilter;
import org.springframework.web.server.WebFilterChain;
import org.springframework.web.util.pattern.PathPattern;
import org.springframework.web.util.pattern.PathPatternParser;
import reactor.core.publisher.Mono;

import java.util.List;

/**
 * Validates JWT access tokens at the edge and forwards the authenticated principal to
 * downstream services.
 *
 * <p>Supports the API Gateway responsibility of validating JWT access tokens (design:
 * "Verified at the Gateway and re-verified at each service"). The filter:
 * <ul>
 *   <li>allows configured public routes (authentication endpoints, health probes) through
 *       without a token;</li>
 *   <li>for every other (protected) route, requires a {@code Bearer} access token and
 *       verifies its signature and expiry;</li>
 *   <li>rejects a missing, malformed, expired, or invalidly signed token with HTTP 401 and
 *       the shared {@link com.aisa.commons.error.ApiError} contract;</li>
 *   <li>on success, strips any client-supplied identity headers and injects the verified
 *       {@code X-User-Id} and {@code X-User-Role} claims for downstream services.</li>
 * </ul>
 *
 * <p>It runs after {@link CorrelationIdWebFilter} so rejections already carry the
 * correlation identifier, and ahead of the gateway routing handler so unauthenticated
 * traffic never reaches a protected downstream service.
 */
@Component
public class JwtAuthenticationFilter implements WebFilter, Ordered {

    /** Run after correlation establishment and before routing. */
    public static final int ORDER = Ordered.HIGHEST_PRECEDENCE + 20;

    /** Forwarded principal headers; cleared from inbound requests to prevent spoofing. */
    public static final String USER_ID_HEADER = "X-User-Id";
    public static final String USER_ROLE_HEADER = "X-User-Role";

    private static final String BEARER_PREFIX = "Bearer ";

    private static final Logger log = LoggerFactory.getLogger(JwtAuthenticationFilter.class);

    private final GatewaySecurityProperties properties;
    private final JwtVerifier jwtVerifier;
    private final GatewayErrorResponseWriter errorWriter;
    private final PathPatternParser pathPatternParser = PathPatternParser.defaultInstance;
    private final List<PathPattern> publicPatterns;

    public JwtAuthenticationFilter(GatewaySecurityProperties properties,
                                   JwtVerifier jwtVerifier,
                                   GatewayErrorResponseWriter errorWriter) {
        this.properties = properties;
        this.jwtVerifier = jwtVerifier;
        this.errorWriter = errorWriter;
        this.publicPatterns = properties.getPublicPaths().stream()
                .map(pathPatternParser::parse)
                .toList();
    }

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, WebFilterChain chain) {
        if (!properties.getJwt().isEnabled() || isPublic(exchange)) {
            return chain.filter(exchange);
        }

        if (!jwtVerifier.isConfigured()) {
            // Fail closed: validation is enabled but no key is configured, so the gateway
            // cannot trust any token on a protected route.
            log.error("JWT validation is enabled but no verification key is configured; "
                    + "rejecting protected request to {}", exchange.getRequest().getPath());
            return errorWriter.write(exchange, HttpStatus.UNAUTHORIZED,
                    ErrorCodes.AUTHENTICATION_FAILED,
                    "Authentication is required but the gateway is not configured to validate tokens.");
        }

        String token = extractBearerToken(exchange.getRequest());
        if (token == null) {
            return errorWriter.write(exchange, HttpStatus.UNAUTHORIZED,
                    ErrorCodes.AUTHENTICATION_FAILED,
                    "A valid Bearer access token is required.");
        }

        final Claims claims;
        try {
            claims = jwtVerifier.verify(token);
        } catch (JwtException | IllegalArgumentException e) {
            log.debug("Rejected request with invalid access token to {}: {}",
                    exchange.getRequest().getPath(), e.getMessage());
            return errorWriter.write(exchange, HttpStatus.UNAUTHORIZED,
                    ErrorCodes.AUTHENTICATION_FAILED,
                    "The access token is invalid or has expired.");
        }

        ServerWebExchange authenticated = withPrincipalHeaders(exchange, claims);
        return chain.filter(authenticated);
    }

    private boolean isPublic(ServerWebExchange exchange) {
        var path = exchange.getRequest().getPath().pathWithinApplication();
        return publicPatterns.stream().anyMatch(pattern -> pattern.matches(path));
    }

    private String extractBearerToken(ServerHttpRequest request) {
        String authorization = request.getHeaders().getFirst(HttpHeaders.AUTHORIZATION);
        if (authorization == null) {
            return null;
        }
        if (authorization.regionMatches(true, 0, BEARER_PREFIX, 0, BEARER_PREFIX.length())) {
            String token = authorization.substring(BEARER_PREFIX.length()).trim();
            return token.isEmpty() ? null : token;
        }
        return null;
    }

    /**
     * Returns an exchange whose request carries the verified principal headers and has any
     * client-supplied identity headers removed (so a caller cannot forge identity).
     */
    private ServerWebExchange withPrincipalHeaders(ServerWebExchange exchange, Claims claims) {
        String subject = claims.getSubject();
        Object role = claims.get("role");
        ServerHttpRequest mutated = exchange.getRequest().mutate()
                .headers(headers -> {
                    headers.remove(USER_ID_HEADER);
                    headers.remove(USER_ROLE_HEADER);
                    if (subject != null) {
                        headers.set(USER_ID_HEADER, subject);
                    }
                    if (role != null) {
                        headers.set(USER_ROLE_HEADER, String.valueOf(role));
                    }
                })
                .build();
        return exchange.mutate().request(mutated).build();
    }

    @Override
    public int getOrder() {
        return ORDER;
    }
}
