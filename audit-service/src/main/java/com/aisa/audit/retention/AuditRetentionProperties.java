package com.aisa.audit.retention;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Retention configuration for audit events (Req 23.3).
 *
 * @param minimumDays the minimum number of days each audit event must be
 *                    retained from its timestamp. Req 23.3 mandates at least
 *                    365 days, so the default and floor is {@code 365}.
 */
@ConfigurationProperties(prefix = "audit.retention")
public record AuditRetentionProperties(Integer minimumDays) {

    public AuditRetentionProperties {
        if (minimumDays == null || minimumDays < 365) {
            // The 365-day minimum is a hard floor; never configure below it.
            minimumDays = 365;
        }
    }
}
