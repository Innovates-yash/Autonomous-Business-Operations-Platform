package com.aisa.gateway.security;

import com.aisa.commons.error.ErrorCodes;
import org.springframework.core.Ordered;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebFilter;
import org.springframework.web.server.WebFilterChain;
import reactor.core.publisher.Mono;

/**
 * Edge-level request size enforcement (Requirement 25.6). Rejects any request whose
 * {@code Content-Length} header exceeds 1 MB (1,048,576 bytes) before the body is
 * buffered.
 *
 * <p>This is a first-pass defense at the gateway. Individual downstream services also
 * enforce the limit via {@code server.tomcat.max-http-form-post-size} and
 * {@code spring.servlet.multipart.max-request-size} for defense in depth.
 *
 * <p>Chunked-transfer requests without a Content-Length header pass through this filter
 * and are bounded by {@code spring.codec.max-in-memory-size=1MB} at the codec level.
 */
@Component
public class RequestSizeLimitWebFilter implements WebFilter, Ordered {

    /** 1 MB in bytes. */
    private static final long MAX_CONTENT_LENGTH = 1_048_576L;

    private final GatewayErrorResponseWriter errorWriter;

    public RequestSizeLimitWebFilter(GatewayErrorResponseWriter errorWriter) {
        this.errorWriter = errorWriter;
    }

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, WebFilterChain chain) {
        long contentLength = exchange.getRequest().getHeaders().getContentLength();
        if (contentLength > MAX_CONTENT_LENGTH) {
            return errorWriter.write(exchange, HttpStatus.PAYLOAD_TOO_LARGE,
                    ErrorCodes.PAYLOAD_TOO_LARGE,
                    "Request payload exceeds the maximum allowed size of 1 MB");
        }
        return chain.filter(exchange);
    }

    /**
     * Runs early in the filter chain (after correlation-id injection at -10, before
     * rate limiting at 0) to reject oversized requests before they consume resources.
     */
    @Override
    public int getOrder() {
        return -5;
    }
}
