package com.aisa.gateway.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;

class JwtAuthenticationFilterTest {

    private static final String SECRET = "test-secret-test-secret-test-secret-1234567890";

    private GatewaySecurityProperties properties;
    private JwtAuthenticationFilter filter;
    private final SecretKey signingKey = Keys.hmacShaKeyFor(SECRET.getBytes(StandardCharsets.UTF_8));

    @BeforeEach
    void setUp() {
        properties = new GatewaySecurityProperties();
        properties.getJwt().setEnabled(true);
        properties.getJwt().setSecret(SECRET);
        JwtVerifier verifier = new JwtVerifier(properties);
        GatewayErrorResponseWriter writer = new GatewayErrorResponseWriter(new ObjectMapper());
        filter = new JwtAuthenticationFilter(properties, verifier, writer);
    }

    private String token(String subject, String role, long expiresInSeconds) {
        long now = System.currentTimeMillis();
        return Jwts.builder()
                .subject(subject)
                .claim("role", role)
                .issuedAt(new Date(now))
                .expiration(new Date(now + expiresInSeconds * 1000))
                .signWith(signingKey)
                .compact();
    }

    @Test
    void allowsPublicAuthRouteWithoutToken() {
        MockServerWebExchange exchange = MockServerWebExchange.from(
                MockServerHttpRequest.post("/api/auth/login"));
        AtomicReference<ServerWebExchange> downstream = new AtomicReference<>();

        StepVerifier.create(filter.filter(exchange, ex -> {
            downstream.set(ex);
            return Mono.empty();
        })).verifyComplete();

        assertThat(downstream.get()).isNotNull();
        assertThat(exchange.getResponse().getStatusCode()).isNull();
    }

    @Test
    void rejectsProtectedRouteWithoutToken() {
        MockServerWebExchange exchange = MockServerWebExchange.from(
                MockServerHttpRequest.get("/api/projects"));

        StepVerifier.create(filter.filter(exchange, ex -> Mono.empty())).verifyComplete();

        assertThat(exchange.getResponse().getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test
    void acceptsValidTokenAndForwardsPrincipalHeaders() {
        String jwt = token("user-123", "ARCHITECT", 900);
        MockServerWebExchange exchange = MockServerWebExchange.from(
                MockServerHttpRequest.get("/api/projects")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + jwt));
        AtomicReference<ServerWebExchange> downstream = new AtomicReference<>();

        StepVerifier.create(filter.filter(exchange, ex -> {
            downstream.set(ex);
            return Mono.empty();
        })).verifyComplete();

        assertThat(downstream.get()).isNotNull();
        assertThat(downstream.get().getRequest().getHeaders()
                .getFirst(JwtAuthenticationFilter.USER_ID_HEADER)).isEqualTo("user-123");
        assertThat(downstream.get().getRequest().getHeaders()
                .getFirst(JwtAuthenticationFilter.USER_ROLE_HEADER)).isEqualTo("ARCHITECT");
    }

    @Test
    void rejectsExpiredToken() {
        String jwt = token("user-123", "ARCHITECT", -3600);
        MockServerWebExchange exchange = MockServerWebExchange.from(
                MockServerHttpRequest.get("/api/projects")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + jwt));

        StepVerifier.create(filter.filter(exchange, ex -> Mono.empty())).verifyComplete();

        assertThat(exchange.getResponse().getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test
    void rejectsTokenWithBadSignature() {
        SecretKey otherKey = Keys.hmacShaKeyFor(
                "another-secret-another-secret-another-secret-99".getBytes(StandardCharsets.UTF_8));
        String jwt = Jwts.builder()
                .subject("user-123")
                .expiration(new Date(System.currentTimeMillis() + 900_000))
                .signWith(otherKey)
                .compact();
        MockServerWebExchange exchange = MockServerWebExchange.from(
                MockServerHttpRequest.get("/api/projects")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + jwt));

        StepVerifier.create(filter.filter(exchange, ex -> Mono.empty())).verifyComplete();

        assertThat(exchange.getResponse().getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test
    void stripsClientSuppliedPrincipalHeaders() {
        String jwt = token("real-user", "CLIENT", 900);
        MockServerWebExchange exchange = MockServerWebExchange.from(
                MockServerHttpRequest.get("/api/projects")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + jwt)
                        .header(JwtAuthenticationFilter.USER_ID_HEADER, "spoofed-admin")
                        .header(JwtAuthenticationFilter.USER_ROLE_HEADER, "ADMIN"));
        AtomicReference<ServerWebExchange> downstream = new AtomicReference<>();

        StepVerifier.create(filter.filter(exchange, ex -> {
            downstream.set(ex);
            return Mono.empty();
        })).verifyComplete();

        assertThat(downstream.get().getRequest().getHeaders()
                .getFirst(JwtAuthenticationFilter.USER_ID_HEADER)).isEqualTo("real-user");
        assertThat(downstream.get().getRequest().getHeaders()
                .getFirst(JwtAuthenticationFilter.USER_ROLE_HEADER)).isEqualTo("CLIENT");
    }

    @Test
    void allowsAllWhenValidationDisabled() {
        properties.getJwt().setEnabled(false);
        MockServerWebExchange exchange = MockServerWebExchange.from(
                MockServerHttpRequest.get("/api/projects"));
        AtomicReference<ServerWebExchange> downstream = new AtomicReference<>();

        StepVerifier.create(filter.filter(exchange, ex -> {
            downstream.set(ex);
            return Mono.empty();
        })).verifyComplete();

        assertThat(downstream.get()).isNotNull();
        assertThat(exchange.getResponse().getStatusCode()).isNull();
    }
}
