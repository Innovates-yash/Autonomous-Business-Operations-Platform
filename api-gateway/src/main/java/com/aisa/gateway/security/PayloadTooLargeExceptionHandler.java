package com.aisa.gateway.security;

import com.aisa.commons.error.ErrorCodes;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.web.reactive.error.ErrorWebExceptionHandler;
import org.springframework.core.Ordered;
import org.springframework.core.io.buffer.DataBufferLimitException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

/**
 * Reactive error handler that catches payload-too-large exceptions thrown by the
 * Spring WebFlux codec layer when a request body exceeds
 * {@code spring.codec.max-in-memory-size} (1 MB per Requirement 25.6).
 *
 * <p>This handles two cases:
 * <ul>
 *   <li>{@link DataBufferLimitException} — thrown by the codec when buffered data
 *       exceeds the configured max-in-memory-size (chunked requests without a
 *       Content-Length header that bypass the {@link RequestSizeLimitWebFilter}).</li>
 *   <li>{@link ResponseStatusException} with status 413 — thrown by downstream
 *       filters or route handlers on oversized payloads.</li>
 * </ul>
 *
 * <p>Returns the same {@link com.aisa.commons.error.ApiError} contract with the
 * {@code PAYLOAD_TOO_LARGE} error code, ensuring no internal details leak
 * (Requirement 25.7).
 *
 * <p>All other exceptions are propagated to the default Spring Boot error handler.
 */
@Component
public class PayloadTooLargeExceptionHandler implements ErrorWebExceptionHandler, Ordered {

    private static final Logger log = LoggerFactory.getLogger(PayloadTooLargeExceptionHandler.class);

    private final GatewayErrorResponseWriter errorWriter;

    public PayloadTooLargeExceptionHandler(GatewayErrorResponseWriter errorWriter) {
        this.errorWriter = errorWriter;
    }

    @Override
    public Mono<Void> handle(ServerWebExchange exchange, Throwable ex) {
        if (ex instanceof DataBufferLimitException) {
            log.warn("Request body exceeded codec buffer limit on {}: {}",
                    exchange.getRequest().getPath(), ex.getMessage());
            return errorWriter.write(exchange, HttpStatus.PAYLOAD_TOO_LARGE,
                    ErrorCodes.PAYLOAD_TOO_LARGE,
                    "Request payload exceeds the maximum allowed size of 1 MB");
        }

        if (ex instanceof ResponseStatusException rse
                && rse.getStatusCode().value() == HttpStatus.PAYLOAD_TOO_LARGE.value()) {
            log.warn("Payload too large on {}: {}",
                    exchange.getRequest().getPath(), ex.getMessage());
            return errorWriter.write(exchange, HttpStatus.PAYLOAD_TOO_LARGE,
                    ErrorCodes.PAYLOAD_TOO_LARGE,
                    "Request payload exceeds the maximum allowed size of 1 MB");
        }

        // Not a payload-size exception — propagate to the next handler in the chain.
        return Mono.error(ex);
    }

    /**
     * High precedence (lower value = earlier) so this handler intercepts
     * DataBufferLimitException before Spring Boot's default error handler attempts
     * to render it with internal details.
     */
    @Override
    public int getOrder() {
        return -2;
    }
}
