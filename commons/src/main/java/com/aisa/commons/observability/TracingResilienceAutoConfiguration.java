package com.aisa.commons.observability;

import brave.baggage.BaggageField;
import brave.baggage.BaggagePropagationConfig.SingleBaggageField;
import brave.baggage.BaggagePropagationCustomizer;
import brave.baggage.CorrelationScopeConfig.SingleCorrelationField;
import brave.baggage.CorrelationScopeCustomizer;
import com.aisa.commons.correlation.CorrelationContext;
import org.springframework.beans.factory.config.BeanPostProcessor;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;

/**
 * Shared distributed-tracing configuration applied to every service that depends on commons.
 *
 * <p>Two cross-cutting concerns are wired here so no service has to repeat them:
 *
 * <ul>
 *   <li><b>Correlation ↔ trace binding (Requirement 27.5).</b> A Brave baggage field named
 *       {@code correlationId} is bound to the {@code X-Correlation-Id} header the API Gateway
 *       injects. The same field is mirrored into the logging MDC under the existing
 *       {@code correlationId} key, so a single unchanged identifier appears on both the logs
 *       and the spans of a request as it flows across services.</li>
 *   <li><b>Telemetry resilience (Requirement 27.7).</b> The Zipkin span exporter is wrapped so
 *       that, when the tracing backend is unreachable, span emission is retried a bounded number
 *       of times and then dropped (fail-open) rather than propagated. The exporter already runs
 *       on a background reporter thread, so neither the retries nor an unreachable backend ever
 *       block request processing.</li>
 * </ul>
 *
 * <p>Spans themselves carry a start timestamp, a duration, and the originating service identifier
 * (from {@code spring.application.name}); that part is produced by Spring Boot's own Brave/Zipkin
 * auto-configuration once the bridge and reporter are on the classpath, which commons supplies
 * transitively (Requirement 27.4).
 *
 * <p>Registered via {@code META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports}.
 */
@AutoConfiguration
@ConditionalOnClass(name = "brave.Tracing")
public class TracingResilienceAutoConfiguration {

    /**
     * Baggage field whose name matches the existing logging MDC key so the correlation identifier
     * keeps showing up under {@code %X{correlationId}} in every service's log pattern.
     */
    static final BaggageField CORRELATION_FIELD = BaggageField.create(CorrelationContext.MDC_KEY);

    /**
     * Propagates the correlation identifier as trace baggage, reading and writing it under the
     * {@code X-Correlation-Id} header used end to end by the gateway and downstream services.
     */
    @Bean
    @ConditionalOnMissingBean(name = "correlationBaggagePropagationCustomizer")
    public BaggagePropagationCustomizer correlationBaggagePropagationCustomizer() {
        return builder -> builder.add(
                SingleBaggageField.newBuilder(CORRELATION_FIELD)
                        .addKeyName(CorrelationContext.CORRELATION_ID_HEADER)
                        .build());
    }

    /**
     * Mirrors the correlation baggage field into the logging MDC under {@code correlationId},
     * flushing on update so a value set mid-request is visible to subsequent log statements.
     */
    @Bean
    @ConditionalOnMissingBean(name = "correlationScopeCustomizer")
    public CorrelationScopeCustomizer correlationScopeCustomizer() {
        return builder -> builder.add(
                SingleCorrelationField.newBuilder(CORRELATION_FIELD)
                        .name(CorrelationContext.MDC_KEY)
                        .flushOnUpdate()
                        .build());
    }

    /**
     * Wraps the Zipkin span sender with bounded-retry, fail-open behaviour (Requirement 27.7).
     * Kept in a nested configuration so it only activates when the Zipkin reporter is present.
     */
    @Configuration(proxyBeanMethods = false)
    @ConditionalOnClass(name = "zipkin2.reporter.BytesMessageSender")
    static class SenderResilienceConfiguration {

        /** Property controlling how many times span emission is retried before giving up. */
        static final String MAX_RETRIES_PROPERTY = "aisa.tracing.emission.max-retries";
        static final int DEFAULT_MAX_RETRIES = 3;

        /**
         * Decorates every {@code BytesMessageSender} bean (the Zipkin span exporter) with a
         * retrying, fail-open delegate. Declared {@code static} so it can post-process
         * infrastructure beans without forcing early initialization of regular beans.
         */
        @Bean
        static BeanPostProcessor tracingSenderRetryBeanPostProcessor(Environment environment) {
            int maxRetries = environment.getProperty(
                    MAX_RETRIES_PROPERTY, Integer.class, DEFAULT_MAX_RETRIES);
            return new TracingSenderRetryBeanPostProcessor(Math.max(0, maxRetries));
        }
    }
}
