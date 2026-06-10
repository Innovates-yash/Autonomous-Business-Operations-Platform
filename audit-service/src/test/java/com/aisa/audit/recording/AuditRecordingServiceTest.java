package com.aisa.audit.recording;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.aisa.audit.domain.AuditAction;
import com.aisa.audit.domain.AuditEvent;
import com.aisa.audit.messaging.AuditEventMessage;
import com.aisa.audit.repository.AuditEventRepository;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.time.Instant;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataAccessResourceFailureException;

/**
 * Unit tests for {@link AuditRecordingService}: durable recording with bounded
 * retry-or-abort (Req 23.1, 23.2).
 */
@ExtendWith(MockitoExtension.class)
class AuditRecordingServiceTest {

    @Mock
    private AuditEventRepository repository;

    private AuditEventMessage message;

    @BeforeEach
    void setUp() {
        message = new AuditEventMessage(
                "action-1", "user-1", AuditAction.AUTHENTICATION, "target-1",
                Instant.parse("2024-01-01T00:00:00.123Z"), "corr-1");
    }

    private AuditRecordingService serviceWithRetries(int retries) {
        AuditRecordingProperties props = new AuditRecordingProperties(retries, 0L);
        return new AuditRecordingService(repository, props, new SimpleMeterRegistry());
    }

    @Test
    void recordsOnFirstAttempt() {
        AuditRecordingService service = serviceWithRetries(3);
        when(repository.save(any(AuditEvent.class))).thenAnswer(inv -> inv.getArgument(0));

        AuditEvent recorded = service.record(message);

        assertThat(recorded.getUserId()).isEqualTo("user-1");
        assertThat(recorded.getAction()).isEqualTo(AuditAction.AUTHENTICATION);
        assertThat(recorded.getTargetId()).isEqualTo("target-1");
        verify(repository, times(1)).save(any(AuditEvent.class));
    }

    @Test
    void retriesTransientFailureThenSucceeds() {
        AuditRecordingService service = serviceWithRetries(3);
        when(repository.save(any(AuditEvent.class)))
                .thenThrow(new DataAccessResourceFailureException("db down"))
                .thenThrow(new DataAccessResourceFailureException("db down"))
                .thenAnswer(inv -> inv.getArgument(0));

        AuditEvent recorded = service.record(message);

        assertThat(recorded).isNotNull();
        // 2 failures + 1 success = 3 total invocations.
        verify(repository, times(3)).save(any(AuditEvent.class));
    }

    @Test
    void abortsAfterExhaustingAllAttempts() {
        // 3 retries -> 4 total attempts (1 initial + 3 retries) per Req 23.2.
        AuditRecordingService service = serviceWithRetries(3);
        when(repository.save(any(AuditEvent.class)))
                .thenThrow(new DataAccessResourceFailureException("db down"));

        assertThatThrownBy(() -> service.record(message))
                .isInstanceOf(AuditRecordingException.class)
                .satisfies(ex -> assertThat(((AuditRecordingException) ex).getAttempts()).isEqualTo(4));

        verify(repository, times(4)).save(any(AuditEvent.class));
    }

    @Test
    void honoursConfiguredRetryCount() {
        // 1 retry -> 2 total attempts.
        AuditRecordingService service = serviceWithRetries(1);
        when(repository.save(any(AuditEvent.class)))
                .thenThrow(new DataAccessResourceFailureException("db down"));

        assertThatThrownBy(() -> service.record(message))
                .isInstanceOf(AuditRecordingException.class);

        verify(repository, times(2)).save(any(AuditEvent.class));
    }
}
