package com.aisa.gateway.security;

import com.aisa.commons.correlation.CorrelationContext;
import org.junit.jupiter.api.Test;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;

class CorrelationIdWebFilterTest {

    private final CorrelationIdWebFilter filter = new CorrelationIdWebFilter();

    @Test
    void generatesCorrelationIdWhenAbsentAndPropagatesDownstreamAndOnResponse() {
        MockServerWebExchange exchange = MockServerWebExchange.from(
                MockServerHttpRequest.get("/api/projects"));
        AtomicReference<ServerWebExchange> downstream = new AtomicReference<>();

        StepVerifier.create(filter.filter(exchange, ex -> {
            downstream.set(ex);
            return Mono.empty();
        })).verifyComplete();

        String downstreamId = downstream.get().getRequest().getHeaders()
                .getFirst(CorrelationContext.CORRELATION_ID_HEADER);
        assertThat(downstreamId).isNotBlank();
        // The same identifier is echoed back to the caller.
        assertThat(exchange.getResponse().getHeaders()
                .getFirst(CorrelationContext.CORRELATION_ID_HEADER)).isEqualTo(downstreamId);
    }

    @Test
    void propagatesProvidedCorrelationIdUnchanged() {
        String provided = "11111111-2222-3333-4444-555555555555";
        MockServerWebExchange exchange = MockServerWebExchange.from(
                MockServerHttpRequest.get("/api/projects")
                        .header(CorrelationContext.CORRELATION_ID_HEADER, provided));
        AtomicReference<ServerWebExchange> downstream = new AtomicReference<>();

        StepVerifier.create(filter.filter(exchange, ex -> {
            downstream.set(ex);
            return Mono.empty();
        })).verifyComplete();

        assertThat(downstream.get().getRequest().getHeaders()
                .getFirst(CorrelationContext.CORRELATION_ID_HEADER)).isEqualTo(provided);
        assertThat(exchange.getResponse().getHeaders()
                .getFirst(CorrelationContext.CORRELATION_ID_HEADER)).isEqualTo(provided);
    }

    @Test
    void generatesIdWhenInboundHeaderIsBlank() {
        MockServerWebExchange exchange = MockServerWebExchange.from(
                MockServerHttpRequest.get("/api/projects")
                        .header(CorrelationContext.CORRELATION_ID_HEADER, "   "));
        AtomicReference<ServerWebExchange> downstream = new AtomicReference<>();

        StepVerifier.create(filter.filter(exchange, ex -> {
            downstream.set(ex);
            return Mono.empty();
        })).verifyComplete();

        assertThat(downstream.get().getRequest().getHeaders()
                .getFirst(CorrelationContext.CORRELATION_ID_HEADER)).isNotBlank().isNotEqualTo("   ");
    }
}
