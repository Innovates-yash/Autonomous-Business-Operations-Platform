package com.aisa.commons.observability;

import io.micrometer.core.instrument.Meter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.config.MeterFilter;
import io.micrometer.core.instrument.distribution.DistributionStatisticConfig;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.context.annotation.Bean;

/**
 * Shared Micrometer configuration applied to every service that depends on commons.
 *
 * <p>Spring Boot's actuator already publishes the {@code http.server.requests} timer, which
 * provides request count, error count (via the {@code outcome}/{@code status} tags) and
 * request latency in a Prometheus-consumable form (Requirement 27.1). This auto-configuration
 * enriches that timer with a percentile histogram and client-side percentiles so Grafana can
 * render latency distributions and quantiles. Prometheus scrapes every service on a 15s
 * interval, keeping values fresh well within the 60s bound (Requirement 27.2).
 *
 * <p>Registered via {@code META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports}
 * so each service picks it up without component-scanning the commons package.
 */
@AutoConfiguration
@ConditionalOnClass(MeterRegistry.class)
public class ObservabilityMetricsAutoConfiguration {

    /** Metric name prefix emitted by Spring Boot for inbound HTTP server requests. */
    static final String HTTP_SERVER_REQUESTS = "http.server.requests";

    /**
     * Enables a percentile histogram and the p50/p95/p99 client-side percentiles for the
     * HTTP server request timer, leaving all other meters untouched.
     */
    @Bean
    public MeterFilter httpServerRequestsHistogramFilter() {
        return new MeterFilter() {
            @Override
            public DistributionStatisticConfig configure(Meter.Id id, DistributionStatisticConfig config) {
                if (id.getName().startsWith(HTTP_SERVER_REQUESTS)) {
                    return DistributionStatisticConfig.builder()
                            .percentilesHistogram(true)
                            .percentiles(0.5, 0.95, 0.99)
                            .build()
                            .merge(config);
                }
                return config;
            }
        };
    }
}
