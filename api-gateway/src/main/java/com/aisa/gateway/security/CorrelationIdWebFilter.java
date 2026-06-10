package com.aisa.gateway.security;

import com.aisa.commons.correlation.CorrelationContext;
import org.slf4j.MDC;
import org.springframework.core.Ordered;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebFilter;
import org.springframework.web.server.WebFilterChain;
import reactor.core.publisher.Mono;

import java.util.UUID;

/**
 * Establishes the correlation identifier for every request entering the platform at the
 * edge and propagates it both downstream and back to the caller.
 *
 * <p>Implements Requirements 27.5 and 27.6:
 * <ul>
 *   <li>27.6 — when an inbound request arrives without a correlation identifier, the
 *       gateway generates a unique one and associates it with the request before any
 *       further processing.</li>
 *   <li>27.5 — the identifier is forwarded unchanged on the downstream request via the
 *       {@code X-Correlation-Id} header, so every service handling the request shares one
 *       identifier end to end (correctness Property 22).</li>
 * </ul>
 *
 * <p>The identifier is also written to the response so clients can quote it for support,
 * placed in the reactor context, and mirrored to the logging MDC so gateway log lines
 * carry it (the {@code logging.pattern.level} uses {@code %X{correlationId}}).
 *
 * <p>The filter runs immediately after the transport-security check and ahead of JWT
 * validation and routing so that any subsequent edge rejection already carries the
 * identifier.
 */
@Component
public class CorrelationIdWebFilter implements WebFilter, Ordered {

    /** Run right after {@link SecureTransportWebFilter} and before JWT validation. */
    public static final int ORDER = Ordered.HIGHEST_PRECEDENCE + 10;

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, WebFilterChain chain) {
        ServerHttpRequest request = exchange.getRequest();
        String correlationId = firstNonBlank(
                request.getHeaders().getFirst(CorrelationContext.CORRELATION_ID_HEADER));
        if (correlationId == null) {
            correlationId = UUID.randomUUID().toString();
        }
        final String resolvedId = correlationId;

        // Forward the (possibly generated) id downstream by rewriting the request header,
        // so every downstream service receives the same identifier.
        ServerHttpRequest mutatedRequest = request.mutate()
                .headers(headers -> headers.set(CorrelationContext.CORRELATION_ID_HEADER, resolvedId))
                .build();
        ServerWebExchange mutatedExchange = exchange.mutate().request(mutatedRequest).build();

        // Echo the identifier back to the caller. Set it before the response commits.
        mutatedExchange.getResponse().getHeaders()
                .set(CorrelationContext.CORRELATION_ID_HEADER, resolvedId);
        mutatedExchange.getResponse().beforeCommit(() -> {
            mutatedExchange.getResponse().getHeaders()
                    .set(CorrelationContext.CORRELATION_ID_HEADER, resolvedId);
            return Mono.empty();
        });

        MDC.put(CorrelationContext.MDC_KEY, resolvedId);
        CorrelationContext.set(resolvedId);
        try {
            return chain.filter(mutatedExchange)
                    .contextWrite(ctx -> ctx.put(CorrelationContext.MDC_KEY, resolvedId))
                    .doFinally(signal -> {
                        MDC.remove(CorrelationContext.MDC_KEY);
                        CorrelationContext.clear();
                    });
        } finally {
            // Avoid leaking the value onto the carrier thread once the reactive chain has
            // been assembled; per-signal cleanup happens in doFinally above.
            MDC.remove(CorrelationContext.MDC_KEY);
            CorrelationContext.clear();
        }
    }

    private String firstNonBlank(String value) {
        return (value == null || value.isBlank()) ? null : value.trim();
    }

    @Override
    public int getOrder() {
        return ORDER;
    }
}
