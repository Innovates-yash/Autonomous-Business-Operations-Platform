package com.aisa.commons.observability;

import io.micrometer.core.instrument.Meter;
import io.micrometer.core.instrument.Tags;
import io.micrometer.core.instrument.config.MeterFilter;
import io.micrometer.core.instrument.distribution.DistributionStatisticConfig;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ObservabilityMetricsAutoConfigurationTest {

    private final MeterFilter filter =
            new ObservabilityMetricsAutoConfiguration().httpServerRequestsHistogramFilter();

    @Test
    void enablesPercentileHistogramForHttpServerRequests() {
        Meter.Id id = new Meter.Id(
                "http.server.requests", Tags.empty(), null, null, Meter.Type.TIMER);

        DistributionStatisticConfig result = filter.configure(id, DistributionStatisticConfig.DEFAULT);

        assertThat(result.isPercentileHistogram()).isTrue();
        assertThat(result.getPercentiles()).containsExactly(0.5, 0.95, 0.99);
    }

    @Test
    void leavesUnrelatedMetersUnchanged() {
        Meter.Id id = new Meter.Id(
                "jvm.memory.used", Tags.empty(), null, null, Meter.Type.GAUGE);

        DistributionStatisticConfig result = filter.configure(id, DistributionStatisticConfig.DEFAULT);

        assertThat(result).isSameAs(DistributionStatisticConfig.DEFAULT);
    }
}
