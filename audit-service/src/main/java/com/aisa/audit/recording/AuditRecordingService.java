package com.aisa.audit.recording;

import com.aisa.audit.domain.AuditEvent;
import com.aisa.audit.messaging.AuditEventMessage;
import com.aisa.audit.repository.AuditEventRepository;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import java.util.concurrent.TimeUnit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * Durably records audit events with bounded retry (Req 23.1, 23.2).
 *
 * <p>An incoming {@link AuditEventMessage} is mapped to an immutable
 * {@link AuditEvent} and inserted into the append-only store. If persistence
 * fails, the insert is retried up to {@link AuditRecordingProperties#retryAttempts()}
 * times. When every attempt fails, an {@link AuditRecordingException} is thrown so
 * the caller can reject the originating action while preserving prior state
 * (audit-or-abort, correctness Property 21).
 *
 * <p>Retries use a small fixed backoff so the worst-case end-to-end recording
 * time remains within the 2-second budget of Req 23.1; the elapsed time per
 * recording is captured as a Micrometer timer for monitoring.
 */
@Service
public class AuditRecordingService {

    private static final Logger log = LoggerFactory.getLogger(AuditRecordingService.class);

    private final AuditEventRepository repository;
    private final AuditRecordingProperties properties;
    private final Timer recordingTimer;

    public AuditRecordingService(AuditEventRepository repository,
                                 AuditRecordingProperties properties,
                                 MeterRegistry meterRegistry) {
        this.repository = repository;
        this.properties = properties;
        this.recordingTimer = Timer.builder("audit.recording.duration")
                .description("Time taken to durably record an audit event, including retries")
                .publishPercentileHistogram()
                .register(meterRegistry);
    }

    /**
     * Records the given audit event, retrying transient persistence failures.
     *
     * @param message the audit-event request to record
     * @return the persisted {@link AuditEvent} (with generated id)
     * @throws AuditRecordingException if recording fails after all attempts
     */
    public AuditEvent record(AuditEventMessage message) {
        long start = System.nanoTime();
        try {
            return doRecordWithRetry(message);
        } finally {
            recordingTimer.record(System.nanoTime() - start, TimeUnit.NANOSECONDS);
        }
    }

    private AuditEvent doRecordWithRetry(AuditEventMessage message) {
        int totalAttempts = properties.totalAttempts();
        RuntimeException lastFailure = null;

        for (int attempt = 1; attempt <= totalAttempts; attempt++) {
            try {
                AuditEvent persisted = repository.save(toEntity(message));
                if (attempt > 1) {
                    log.info("Recorded audit event for action={} target={} on attempt {}/{}",
                            message.action(), message.targetId(), attempt, totalAttempts);
                }
                return persisted;
            } catch (RuntimeException ex) {
                lastFailure = ex;
                log.warn("Audit recording attempt {}/{} failed for action={} target={}: {}",
                        attempt, totalAttempts, message.action(), message.targetId(),
                        ex.getMessage());
                if (attempt < totalAttempts) {
                    backoff();
                }
            }
        }

        throw new AuditRecordingException(totalAttempts,
                "Failed to record audit event after " + totalAttempts + " attempts", lastFailure);
    }

    private void backoff() {
        long delay = properties.retryBackoffMillis();
        if (delay <= 0) {
            return;
        }
        try {
            Thread.sleep(delay);
        } catch (InterruptedException ie) {
            Thread.currentThread().interrupt();
        }
    }

    private AuditEvent toEntity(AuditEventMessage message) {
        return new AuditEvent(
                message.userId(),
                message.action(),
                message.targetId(),
                message.occurredAt(),
                message.correlationId());
    }
}
