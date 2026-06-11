package com.aisa.provider.gateway;

import com.aisa.provider.repository.ProviderUsageRecordRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;

/**
 * Prunes provider usage records once they fall outside the configured retention window
 * (Requirement 20.8).
 *
 * <p>The retention period is configurable but never drops below the 90-day floor (enforced by
 * {@link GatewayProperties#getUsageRetentionDays()}), so records are always retained for at least
 * 90 days. Pruning runs on a daily schedule and is safe to run on every instance — deleting
 * already-deleted rows is a no-op.
 */
@Service
public class UsageRetentionService {

    private static final Logger log = LoggerFactory.getLogger(UsageRetentionService.class);

    private final ProviderUsageRecordRepository usageRepository;
    private final GatewayProperties properties;

    public UsageRetentionService(ProviderUsageRecordRepository usageRepository,
                                 GatewayProperties properties) {
        this.usageRepository = usageRepository;
        this.properties = properties;
    }

    /** @return the instant before which usage records are eligible for pruning. */
    public Instant retentionCutoff() {
        return Instant.now().minus(Duration.ofDays(properties.getUsageRetentionDays()));
    }

    /**
     * Delete usage records older than the retention window. Scheduled daily; also invokable
     * directly in tests.
     *
     * @return the number of records pruned
     */
    @Transactional
    @Scheduled(fixedDelayString = "${aisa.provider.gateway.retention-prune-ms:86400000}")
    public long pruneExpiredRecords() {
        Instant cutoff = retentionCutoff();
        long removed = usageRepository.deleteByServedAtBefore(cutoff);
        if (removed > 0) {
            log.info("Pruned {} provider usage records served before {}", removed, cutoff);
        }
        return removed;
    }
}
