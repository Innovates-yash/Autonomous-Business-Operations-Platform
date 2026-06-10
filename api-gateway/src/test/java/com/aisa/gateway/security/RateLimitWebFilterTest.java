package com.aisa.gateway.security;

import com.aisa.commons.error.ErrorCodes;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.ReactiveStringRedisTemplate;
import org.springframework.data.redis.core.ReactiveValueOperations;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.time.Duration;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class RateLimitWebFilterTest {

    private GatewaySecurityProperties properties;
    private ReactiveStringRedisTemplate redisTemplate;
    private ReactiveValueOperations<String, String> valueOps;
    private RateLimitWebFilter filter;

    @BeforeEach
    @SuppressWarnings("unchecked")
    void setUp() {
        properties = new GatewaySecurityProperties();
        properties.getRateLimit().setEnabled(true);
        properties.getRateLimit().setLimit(100);
        properties.getRateLimit().setWindowSeconds(60);

        redisTemplate = mock(ReactiveStringRedisTemplate.class);
        valueOps = mock(ReactiveValueOperations.class);
        when(redisTemplate.opsForValue()).thenReturn(valueOps);

        GatewayErrorResponseWriter writer = new GatewayErrorResponseWriter(new ObjectMapper());
        filter = new RateLimitWebFilter(properties, redisTemplate, writer);
    }

    @Test
    void allowsRequestUnderLimitAndSetsWindowOnFirstRequest() {
        when(valueOps.increment(anyString())).thenReturn(Mono.just(1L));
        when(redisTemplate.expire(anyString(), eq(Duration.ofSeconds(60)))).thenReturn(Mono.just(true));

        MockServerWebExchange exchange = MockServerWebExchange.from(
                MockServerHttpRequest.get("/api/projects").header(JwtAuthenticationFilter.USER_ID_HEADER, "user-1"));
        AtomicReference<ServerWebExchange> downstream = new AtomicReference<>();

        StepVerifier.create(filter.filter(exchange, ex -> {
            downstream.set(ex);
            return Mono.empty();
        })).verifyComplete();

        assertThat(downstream.get()).isNotNull();
        assertThat(exchange.getResponse().getStatusCode()).isNull();
    }

    @Test
    void allowsRequestAtTheLimit() {
        when(valueOps.increment(anyString())).thenReturn(Mono.just(100L));
        when(redisTemplate.getExpire(anyString())).thenReturn(Mono.just(Duration.ofSeconds(30)));

        MockServerWebExchange exchange = MockServerWebExchange.from(
                MockServerHttpRequest.get("/api/projects").header(JwtAuthenticationFilter.USER_ID_HEADER, "user-1"));
        AtomicReference<ServerWebExchange> downstream = new AtomicReference<>();

        StepVerifier.create(filter.filter(exchange, ex -> {
            downstream.set(ex);
            return Mono.empty();
        })).verifyComplete();

        assertThat(downstream.get()).isNotNull();
        assertThat(exchange.getResponse().getStatusCode()).isNull();
    }

    @Test
    void rejectsRequestOverLimitWith429AndRetryAfter() {
        when(valueOps.increment(anyString())).thenReturn(Mono.just(101L));
        when(redisTemplate.getExpire(anyString())).thenReturn(Mono.just(Duration.ofSeconds(42)));

        MockServerWebExchange exchange = MockServerWebExchange.from(
                MockServerHttpRequest.get("/api/projects").header(JwtAuthenticationFilter.USER_ID_HEADER, "user-1"));
        AtomicReference<Boolean> routed = new AtomicReference<>(false);

        StepVerifier.create(filter.filter(exchange, ex -> {
            routed.set(true);
            return Mono.empty();
        })).verifyComplete();

        assertThat(routed.get()).isFalse();
        assertThat(exchange.getResponse().getStatusCode()).isEqualTo(HttpStatus.TOO_MANY_REQUESTS);
        assertThat(exchange.getResponse().getHeaders().getFirst(HttpHeaders.RETRY_AFTER)).isEqualTo("42");
    }

    @Test
    void resetsWindowWhenCounterHasNoExpiry() {
        // Counter present but TTL missing (orphaned key): a fresh window must be applied.
        when(valueOps.increment(anyString())).thenReturn(Mono.just(5L));
        when(redisTemplate.getExpire(anyString())).thenReturn(Mono.just(Duration.ZERO));
        when(redisTemplate.expire(anyString(), eq(Duration.ofSeconds(60)))).thenReturn(Mono.just(true));

        MockServerWebExchange exchange = MockServerWebExchange.from(
                MockServerHttpRequest.get("/api/projects").header(JwtAuthenticationFilter.USER_ID_HEADER, "user-1"));
        AtomicReference<ServerWebExchange> downstream = new AtomicReference<>();

        StepVerifier.create(filter.filter(exchange, ex -> {
            downstream.set(ex);
            return Mono.empty();
        })).verifyComplete();

        assertThat(downstream.get()).isNotNull();
        assertThat(exchange.getResponse().getStatusCode()).isNull();
    }

    @Test
    void failsOpenWhenRedisErrors() {
        when(valueOps.increment(anyString())).thenReturn(Mono.error(new RuntimeException("redis down")));

        MockServerWebExchange exchange = MockServerWebExchange.from(
                MockServerHttpRequest.get("/api/projects").header(JwtAuthenticationFilter.USER_ID_HEADER, "user-1"));
        AtomicReference<ServerWebExchange> downstream = new AtomicReference<>();

        StepVerifier.create(filter.filter(exchange, ex -> {
            downstream.set(ex);
            return Mono.empty();
        })).verifyComplete();

        assertThat(downstream.get()).isNotNull();
        assertThat(exchange.getResponse().getStatusCode()).isNull();
    }

    @Test
    void skipsActuatorProbes() {
        MockServerWebExchange exchange = MockServerWebExchange.from(
                MockServerHttpRequest.get("/actuator/health"));
        AtomicReference<ServerWebExchange> downstream = new AtomicReference<>();

        StepVerifier.create(filter.filter(exchange, ex -> {
            downstream.set(ex);
            return Mono.empty();
        })).verifyComplete();

        assertThat(downstream.get()).isNotNull();
    }

    @Test
    void rejectsUnauthenticatedClientByIpKey() {
        AtomicReference<String> keyUsed = new AtomicReference<>();
        when(valueOps.increment(anyString())).thenAnswer(invocation -> {
            keyUsed.set(invocation.getArgument(0));
            return Mono.just(101L);
        });
        when(redisTemplate.getExpire(anyString())).thenReturn(Mono.just(Duration.ofSeconds(10)));

        MockServerWebExchange exchange = MockServerWebExchange.from(
                MockServerHttpRequest.get("/api/projects").header("X-Forwarded-For", "203.0.113.7, 10.0.0.1"));

        StepVerifier.create(filter.filter(exchange, ex -> Mono.empty())).verifyComplete();

        assertThat(exchange.getResponse().getStatusCode()).isEqualTo(HttpStatus.TOO_MANY_REQUESTS);
        assertThat(keyUsed.get()).isEqualTo(RateLimitWebFilter.KEY_PREFIX + "ip:203.0.113.7");
    }

    @Test
    void rateLimitErrorUsesSharedContract() {
        when(valueOps.increment(anyString())).thenReturn(Mono.just(200L));
        when(redisTemplate.getExpire(anyString())).thenReturn(Mono.just(Duration.ofSeconds(15)));

        MockServerWebExchange exchange = MockServerWebExchange.from(
                MockServerHttpRequest.get("/api/projects").header(JwtAuthenticationFilter.USER_ID_HEADER, "user-1"));

        StepVerifier.create(filter.filter(exchange, ex -> Mono.empty())).verifyComplete();

        assertThat(exchange.getResponse().getStatusCode()).isEqualTo(HttpStatus.TOO_MANY_REQUESTS);
        // The body is the shared ApiError JSON with the stable RATE_LIMITED code.
        String body = exchange.getResponse().getBodyAsString().block();
        assertThat(body).contains(ErrorCodes.RATE_LIMITED);
    }
}
