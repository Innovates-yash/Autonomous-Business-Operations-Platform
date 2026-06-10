package com.aisa.gateway.security;

import com.aisa.commons.error.ErrorCodes;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.Ordered;
import org.springframework.data.redis.core.ReactiveStringRedisTemplate;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebFilter;
import org.springframework.web.server.WebFilterChain;
import reactor.core.publisher.Mono;

import java.net.InetSocketAddress;
import java.time.Duration;

/**
 * Enforces edge rate limiting per client using a Redis-backed fixed window.
 *
 * <p>Implements Requirements 25.4 and 25.5:
 * <ul>
 *   <li>25.4 — when a client exceeds the configured quota (default 100 requests) within
 *       the fixed window (default 60 seconds), further requests are rejected for the
 *       remainder of that window with HTTP 429 and a {@code Retry-After} header carrying
 *       the seconds until the window resets, using the shared {@link com.aisa.commons.error.ApiError}
 *       contract;</li>
 *   <li>25.5 — when the window elapses the per-client counter expires in Redis, so the
 *       client is accepted again on the next request.</li>
 * </ul>
 *
 * <p>The counter is keyed by the authenticated principal forwarded by
 * {@link JwtAuthenticationFilter} ({@code X-User-Id}); for unauthenticated traffic it
 * falls back to the client IP. Keeping the counter in Redis means the limit is enforced
 * consistently across stateless gateway instances (design: "Rate-limit counters live in
 * Redis keyed by principal + window").
 *
 * <p>The filter runs after JWT validation so the verified principal header is available,
 * and ahead of routing so throttled traffic never reaches a downstream service. Actuator
 * probe paths are excluded so infrastructure health checks are never throttled. If the
 * Redis round trip fails the filter fails open (allows the request) rather than taking the
 * edge down on a cache outage.
 */
@Component
public class RateLimitWebFilter implements WebFilter, Ordered {

    /** Run after {@link JwtAuthenticationFilter} (principal resolved) and before routing. */
    public static final int ORDER = JwtAuthenticationFilter.ORDER + 10;

    /** Redis key namespace for rate-limit counters. */
    static final String KEY_PREFIX = "gw:ratelimit:";

    private static final String FORWARDED_FOR_HEADER = "X-Forwarded-For";

    private static final Logger log = LoggerFactory.getLogger(RateLimitWebFilter.class);

    private final GatewaySecurityProperties properties;
    private final ReactiveStringRedisTemplate redisTemplate;
    private final GatewayErrorResponseWriter errorWriter;

    public RateLimitWebFilter(GatewaySecurityProperties properties,
                              ReactiveStringRedisTemplate redisTemplate,
                              GatewayErrorResponseWriter errorWriter) {
        this.properties = properties;
        this.redisTemplate = redisTemplate;
        this.errorWriter = errorWriter;
    }

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, WebFilterChain chain) {
        GatewaySecurityProperties.RateLimit cfg = properties.getRateLimit();
        if (!cfg.isEnabled() || isExcluded(exchange)) {
            return chain.filter(exchange);
        }

        long window = Math.max(1, cfg.getWindowSeconds());
        long limit = Math.max(1, cfg.getLimit());
        String redisKey = KEY_PREFIX + resolveClientKey(exchange);

        return redisTemplate.opsForValue().increment(redisKey)
                .flatMap(count -> ensureWindow(redisKey, count, window)
                        .flatMap(retryAfter -> {
                            if (count > limit) {
                                return reject(exchange, retryAfter);
                            }
                            return chain.filter(exchange);
                        }))
                .onErrorResume(error -> {
                    // Fail open: a Redis outage must not take the edge down (Req 26 resilience).
                    log.warn("Rate-limit check failed for {}; allowing request (fail-open): {}",
                            exchange.getRequest().getPath(), error.toString());
                    return chain.filter(exchange);
                });
    }

    /**
     * Ensures the counter has a window TTL and returns the seconds remaining until reset.
     * The TTL is (re)applied on the first request of a window and whenever a counter is
     * found without an expiry (defensive against an orphaned key), so every window resets
     * after at most {@code windowSeconds} (Req 25.5).
     */
    private Mono<Long> ensureWindow(String redisKey, Long count, long window) {
        if (count != null && count == 1L) {
            return redisTemplate.expire(redisKey, Duration.ofSeconds(window)).thenReturn(window);
        }
        return redisTemplate.getExpire(redisKey)
                .flatMap(ttl -> {
                    long seconds = ttl == null ? 0 : ttl.getSeconds();
                    if (seconds > 0) {
                        return Mono.just(seconds);
                    }
                    return redisTemplate.expire(redisKey, Duration.ofSeconds(window)).thenReturn(window);
                })
                .defaultIfEmpty(window);
    }

    private Mono<Void> reject(ServerWebExchange exchange, long retryAfterSeconds) {
        exchange.getResponse().getHeaders().set(HttpHeaders.RETRY_AFTER, Long.toString(retryAfterSeconds));
        return errorWriter.write(exchange, HttpStatus.TOO_MANY_REQUESTS, ErrorCodes.RATE_LIMITED,
                "Rate limit exceeded. Retry after " + retryAfterSeconds + " seconds.");
    }

    /** Actuator probe paths are not subject to the client quota. */
    private boolean isExcluded(ServerWebExchange exchange) {
        return exchange.getRequest().getPath().pathWithinApplication().value().startsWith("/actuator");
    }

    /**
     * Resolves the rate-limit key for the request: the verified principal when present,
     * otherwise the client IP. Distinct namespaces prevent a user id from colliding with
     * an IP literal.
     */
    private String resolveClientKey(ServerWebExchange exchange) {
        String userId = exchange.getRequest().getHeaders().getFirst(JwtAuthenticationFilter.USER_ID_HEADER);
        if (userId != null && !userId.isBlank()) {
            return "user:" + userId.trim();
        }
        return "ip:" + clientIp(exchange.getRequest());
    }

    private String clientIp(ServerHttpRequest request) {
        String forwardedFor = request.getHeaders().getFirst(FORWARDED_FOR_HEADER);
        if (forwardedFor != null && !forwardedFor.isBlank()) {
            int comma = forwardedFor.indexOf(',');
            String first = (comma >= 0 ? forwardedFor.substring(0, comma) : forwardedFor).trim();
            if (!first.isEmpty()) {
                return first;
            }
        }
        InetSocketAddress remote = request.getRemoteAddress();
        if (remote != null && remote.getAddress() != null) {
            return remote.getAddress().getHostAddress();
        }
        return "unknown";
    }

    @Override
    public int getOrder() {
        return ORDER;
    }
}
