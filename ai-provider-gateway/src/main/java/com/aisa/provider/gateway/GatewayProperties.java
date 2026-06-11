package com.aisa.provider.gateway;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * Tunable thresholds for the routing facade (Requirement 20.5, 20.6, 20.8).
 *
 * <p>Bound from {@code aisa.provider.gateway.*}. Every value has a safe default so the gateway
 * behaves correctly with no extra configuration:
 * <ul>
 *   <li>{@link #consecutiveErrorThreshold} — number of consecutive transport/service errors that
 *       classifies a provider unavailable (Req 20.5, default 3).</li>
 *   <li>{@link #maxFallbacks} — maximum number of fallback providers attempted in priority order
 *       after the selected provider is classified unavailable (Req 20.6, default 3).</li>
 *   <li>{@link #usageRetentionDays} — how long usage records are retained; held at or above the
 *       90-day floor required by Req 20.8 (default 90).</li>
 * </ul>
 */
@Component
@ConfigurationProperties(prefix = "aisa.provider.gateway")
public class GatewayProperties {

    /** Minimum permitted usage retention, in days (Req 20.8). */
    public static final int MIN_RETENTION_DAYS = 90;

    private int consecutiveErrorThreshold = 3;
    private int maxFallbacks = 3;
    private int usageRetentionDays = MIN_RETENTION_DAYS;

    public int getConsecutiveErrorThreshold() {
        return consecutiveErrorThreshold;
    }

    public void setConsecutiveErrorThreshold(int consecutiveErrorThreshold) {
        this.consecutiveErrorThreshold = Math.max(1, consecutiveErrorThreshold);
    }

    public int getMaxFallbacks() {
        return maxFallbacks;
    }

    public void setMaxFallbacks(int maxFallbacks) {
        this.maxFallbacks = Math.max(0, maxFallbacks);
    }

    /**
     * @return the configured retention in days, never below the 90-day floor (Req 20.8).
     */
    public int getUsageRetentionDays() {
        return Math.max(MIN_RETENTION_DAYS, usageRetentionDays);
    }

    public void setUsageRetentionDays(int usageRetentionDays) {
        this.usageRetentionDays = usageRetentionDays;
    }
}
