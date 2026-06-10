package com.aisa.gateway.security;

import com.aisa.commons.error.ApiError;
import com.aisa.commons.error.ErrorCodes;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.nio.charset.StandardCharsets;

/**
 * Writes the shared {@link ApiError} contract as a JSON response body from an edge filter,
 * short-circuiting the filter chain.
 *
 * <p>Centralizing the serialization keeps every edge rejection (transport, authentication,
 * rate limit) on the same client-safe error envelope (Requirement 25.7) and ensures the
 * active correlation identifier is echoed back to the caller for support and tracing
 * (Requirements 27.5–27.6).
 */
@Component
public class GatewayErrorResponseWriter {

    private final ObjectMapper objectMapper;

    public GatewayErrorResponseWriter(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    /**
     * Writes an {@link ApiError} body with the given status and stable error code. The
     * correlation identifier is taken from the {@code X-Correlation-Id} request header,
     * which the correlation filter guarantees is present by this point in the chain.
     */
    public Mono<Void> write(ServerWebExchange exchange, HttpStatus status, String code, String message) {
        ServerHttpResponse response = exchange.getResponse();
        response.setStatusCode(status);
        response.getHeaders().setContentType(MediaType.APPLICATION_JSON);

        String correlationId = exchange.getRequest().getHeaders()
                .getFirst(com.aisa.commons.correlation.CorrelationContext.CORRELATION_ID_HEADER);
        ApiError error = ApiError.of(code, message, correlationId);

        byte[] body;
        try {
            body = objectMapper.writeValueAsBytes(error);
        } catch (JsonProcessingException e) {
            body = ("{\"code\":\"" + (code == null ? ErrorCodes.INTERNAL_ERROR : code) + "\"}")
                    .getBytes(StandardCharsets.UTF_8);
        }
        DataBuffer buffer = response.bufferFactory().wrap(body);
        return response.writeWith(Mono.just(buffer));
    }
}
