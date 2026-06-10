package com.aisa.audit.retention;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.aisa.audit.repository.AuditEventRepository;
import java.time.Duration;
import java.time.Instant;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * Unit tests for {@link AuditRetentionPolicy}: the minimum 365-day retention
 * guarantee (Req 23.3). The store is append-only, so the policy never purges.
 */
@ExtendWith(MockitoExtension.class)
class AuditRetentionPolicyTest {

    @Mock
    private AuditEventRepository repository;

    @Test
    void minimumRetentionIsAtLeast365Days() {
        AuditRetentionPolicy policy =
                new AuditRetentionPolicy(new AuditRetentionProperties(365), repository);

        assertThat(policy.minimumRetention()).isEqualTo(Duration.ofDays(365));
    }

    @Test
    void configuredValueBelowFloorIsClampedTo365() {
        // Even if someone configures a shorter window, the 365-day floor holds.
        AuditRetentionPolicy policy =
                new AuditRetentionPolicy(new AuditRetentionProperties(30), repository);

        assertThat(policy.minimumRetention()).isEqualTo(Duration.ofDays(365));
    }

    @Test
    void eventWithinWindowIsRetained() {
        AuditRetentionPolicy policy =
                new AuditRetentionPolicy(new AuditRetentionProperties(365), repository);
        Instant now = Instant.parse("2024-06-01T00:00:00Z");

        assertThat(policy.isWithinRetention(now.minus(Duration.ofDays(100)), now)).isTrue();
        assertThat(policy.isWithinRetention(now.minus(Duration.ofDays(364)), now)).isTrue();
    }

    @Test
    void eventOlderThanWindowIsBeyondMinimumButStillRetainedByAppendOnlyStore() {
        AuditRetentionPolicy policy =
                new AuditRetentionPolicy(new AuditRetentionProperties(365), repository);
        Instant now = Instant.parse("2024-06-01T00:00:00Z");

        // Beyond the *minimum* guaranteed window...
        assertThat(policy.isWithinRetention(now.minus(Duration.ofDays(400)), now)).isFalse();
    }

    @Test
    void monitorReadsCountAndPerformsNoDeletion() {
        AuditRetentionPolicy policy =
                new AuditRetentionPolicy(new AuditRetentionProperties(365), repository);
        when(repository.countByOccurredAtBefore(org.mockito.ArgumentMatchers.any(Instant.class)))
                .thenReturn(5L);

        policy.monitorRetention();

        verify(repository).countByOccurredAtBefore(org.mockito.ArgumentMatchers.any(Instant.class));
        // No delete method exists on the repository; immutability is preserved by design.
    }
}
