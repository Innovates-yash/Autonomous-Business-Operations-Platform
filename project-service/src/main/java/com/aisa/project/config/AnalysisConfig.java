package com.aisa.project.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/**
 * Configuration properties for requirement analysis (Requirements 4.1, 4.9).
 *
 * <p>The analysis must complete within a configurable timeout (default 60 seconds).
 * If the AI provider does not respond within this window the analysis fails with a
 * timeout indication so the Project's prior state is preserved (Requirement 4.9).
 *
 * <p>On provider failure, the analysis retries up to {@link #maxRetries} times
 * (default 3 total attempts). If all retries are exhausted, analysis halts, prior
 * state and existing requirements are preserved, and a provider-failure error is
 * returned (Requirement 4.9).
 */
@Configuration
@ConfigurationProperties(prefix = "analysis")
public class AnalysisConfig {

    /** Maximum time in seconds for an AI analysis call to complete. Default: 60. */
    private long timeoutSeconds = 60;

    /** Maximum total attempts for the AI analysis call (initial + retries). Default: 3. */
    private int maxRetries = 3;

    public long getTimeoutSeconds() {
        return timeoutSeconds;
    }

    public void setTimeoutSeconds(long timeoutSeconds) {
        this.timeoutSeconds = timeoutSeconds;
    }

    public int getMaxRetries() {
        return maxRetries;
    }

    public void setMaxRetries(int maxRetries) {
        this.maxRetries = maxRetries;
    }
}
