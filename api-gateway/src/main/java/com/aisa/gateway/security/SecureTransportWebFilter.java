package com.aisa.gateway.security;

import com.aisa.commons.correlation.CorrelationContext;
import com.aisa.commons.error.ApiError;
import com.aisa.commons.error.ErrorCodes;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.Ordered;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebFilter;
import org.springframework.web.server.WebFilterChain;
import reactor.core.publisher.Mono;

import java.nio.charset.StandardCharsets;

/**
 * Rejects requests that arrive over an unencrypted transport channel before any routing
 * or payload processing occurs.
 *
 * <p>Implements Requirement 25.2: a client attempting to connect over an unencrypted
 * channel is rejected with an error indicating that encrypted transport is required,
 * without processing the request payload. It complements TLS termination at the gateway
 * (Requirement 25.1) and the {@code X-Forwarded-Proto} contract used when TLS is
 * terminated by an upstream load balancer or ingress.
 *
 * <p>The filter runs at the highest precedence so the rejection short-circuits the chain
 * ahead of the gateway routing handler; the request body is never read or proxied.
 */
@Component
public class SecureTransportWebFilter implements WebFilter, Ordered {

    private static final Logger log = LoggerFactory.getLogger(SecureTransportWebFilter.class);

    private static final String FORWARDED_PROTO_HEADER = "X-Forwarded-Proto";
    private static final String FORWARDED_SSL_HEADER = "X-Forwarded-Ssl";

    private final GatewaySecurityProperties properties;
    private final ObjectMapper objectMapper;

    public SecureTransportWebFilter(GatewaySecurityProperties properties, ObjectMapper objectMapper) {
        this.properties = properties;
        this.objectMapper = objectMapper;
    }

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, WebFilterChain chain) {
        if (!properties.isRequireSecureTransport() || isSecure(exchange.getRequest())) {
            return chain.filter(exchange);
        }
        return rejectUnencrypted(exchange);
    }

    /**
     * Determines whether the request reached the gateway over an encrypted channel.
     * TLS terminated at the gateway exposes SSL session info directly; TLS terminated
     * upstream is signalled by the standard forwarded-protocol headers.
     */
    private boolean isSecure(ServerHttpRequest request) {
        if (request.getSslInfo() != null) {
            return true;
        }
        String scheme = request.getURI().getScheme();
        if ("https".equalsIgnoreCase(scheme) || "wss".equalsIgnoreCase(scheme)) {
            return true;
        }
        HttpHeaders headers = request.getHeaders();
        String forwardedProto = firstToken(headers.getFirst(FORWARDED_PROTO_HEADER));
        if ("https".equalsIgnoreCase(forwardedProto) || "wss".equalsIgnoreCase(forwardedProto)) {
            return true;
        }
        return "on".equalsIgnoreCase(headers.getFirst(FORWARDED_SSL_HEADER));
    }

    private String firstToken(String headerValue) {
        if (headerValue == null) {
            return null;
        }
        int comma = headerValue.indexOf(',');
        return (comma >= 0 ? headerValue.substring(0, comma) : headerValue).trim();
    }

    private Mono<Void> rejectUnencrypted(ServerWebExchange exchange) {
        ServerHttpResponse response = exchange.getResponse();
        response.setStatusCode(HttpStatus.FORBIDDEN);
        response.getHeaders().setContentType(MediaType.APPLICATION_JSON);

        String correlationId = exchange.getRequest().getHeaders()
                .getFirst(CorrelationContext.CORRELATION_ID_HEADER);
        ApiError error = ApiError.of(
                ErrorCodes.ENCRYPTED_TRANSPORT_REQUIRED,
                "Encrypted transport (TLS) is required. Reconnect over HTTPS/WSS.",
                correlationId);

        log.warn("Rejected unencrypted request to {} [{}]",
                exchange.getRequest().getPath(), CorrelationContext.CORRELATION_ID_HEADER);

        byte[] body;
        try {
            body = objectMapper.writeValueAsBytes(error);
        } catch (JsonProcessingException e) {
            body = ("{\"code\":\"" + ErrorCodes.ENCRYPTED_TRANSPORT_REQUIRED + "\"}")
                    .getBytes(StandardCharsets.UTF_8);
        }
        DataBuffer buffer = response.bufferFactory().wrap(body);
        return response.writeWith(Mono.just(buffer));
    }

    @Override
    public int getOrder() {
        // Run ahead of the gateway routing handler so no payload is processed.
        return Ordered.HIGHEST_PRECEDENCE;
    }
}
