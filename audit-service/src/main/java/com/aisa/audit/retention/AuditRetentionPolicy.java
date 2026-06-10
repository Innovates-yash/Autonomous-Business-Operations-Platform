package com.aisa.audit.retention;

import com.aisa.audit.repository.AuditEventRepository;
import java.time.Duration;
import java.time.Instant;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Enforces and monitors the audit-event retention guarantee (Req 23.3): every
 * audit event is retained for a minimum of 365 days from its timestamp.
 *
 * <p><strong>Why there is no purge.</strong> The {@code audit_event} store is
 * append-only and immutable by construction (Req 23.7): DELETE is revoked and
 * additionally blocked by a database trigger, so events are <em>never</em>
 * removed. Retention is therefore satisfied automatically — every event lives
 * well beyond the 365-day minimum. A time-based purge would both violate the
 * immutability guarantee and be rejected at the data layer, so this policy
 * deliberately performs no deletion.
 *
 * <p>This component instead provides the retention <em>contract</em> (the
 * earliest instant still guaranteed to be retained) and a lightweight periodic
 * monitor that confirms historical events remain present, surfacing the
 * retention posture in the logs for operators and audits.
 */
@Component
public class AuditRetentionPolicy {

    private static final Logger log = LoggerFactory.getLogger(AuditRetentionPolicy.class);

    private final AuditRetentionProperties properties;
    private final AuditEventRepository repository;

    public AuditRetentionPolicy(AuditRetentionProperties properties,
                                AuditEventRepository repository) {
        this.properties = properties;
        this.repository = repository;
    }

    /** The minimum retention duration guaranteed for every audit event (Req 23.3). */
    public Duration minimumRetention() {
        return Duration.ofDays(properties.minimumDays());
    }

    /**
     * The earliest timestamp that must still be retained as of {@code now}.
     * Every audit event with a timestamp at or after this instant is, by policy,
     * guaranteed to be present.
     */
    public Instant retainedSince(Instant now) {
        return now.minus(minimumRetention());
    }

    /**
     * Whether an event with the given timestamp is still within the minimum
     * retention window as of {@code now}.
     */
    public boolean isWithinRetention(Instant occurredAt, Instant now) {
        return !occurredAt.isBefore(retainedSince(now));
    }

    /**
     * Periodically confirms the retention posture: because the store is
     * append-only, events older than the retention window are expected to still
     * be present. This emits a daily, low-cost observation rather than mutating
     * data. Disabled outside scheduling by the absence of {@code @EnableScheduling}
     * is irrelevant here; the application enables scheduling centrally.
     */
    @Scheduled(cron = "${audit.retention.monitor-cron:0 0 3 * * *}")
    public void monitorRetention() {
        Instant cutoff = retainedSince(Instant.now());
        long retainedBeyondWindow = repository.countByOccurredAtBefore(cutoff);
        log.info("Audit retention check: minimumDays={} retainedSince={} eventsOlderThanWindow={} "
                        + "(append-only store; no purge performed)",
                properties.minimumDays(), cutoff, retainedBeyondWindow);
    }
}
