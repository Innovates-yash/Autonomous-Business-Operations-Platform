package com.aisa.audit.recording;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Tunables for durable audit recording (Req 23.1, 23.2).
 *
 * @param retryAttempts number of <em>retries</em> attempted after an initial
 *                      persistence failure before the originating action is
 *                      rejected. Req 23.2 mandates "up to 3 times", so the
 *                      default is {@code 3} (a worst case of 4 total persistence
 *                      attempts: 1 initial + 3 retries).
 * @param retryBackoffMillis fixed delay between attempts. Kept small so the
 *                      worst-case total recording time stays comfortably within
 *                      the 2-second budget of Req 23.1.
 */
@ConfigurationProperties(prefix = "audit.recording")
public record AuditRecordingProperties(
        Integer retryAttempts,
        Long retryBackoffMillis) {

    public AuditRecordingProperties {
        if (retryAttempts == null || retryAttempts < 0) {
            retryAttempts = 3;
        }
        if (retryBackoffMillis == null || retryBackoffMillis < 0) {
            retryBackoffMillis = 100L;
        }
    }

    /** Total persistence attempts = 1 initial attempt plus {@link #retryAttempts} retries. */
    public int totalAttempts() {
        return 1 + retryAttempts;
    }
}
