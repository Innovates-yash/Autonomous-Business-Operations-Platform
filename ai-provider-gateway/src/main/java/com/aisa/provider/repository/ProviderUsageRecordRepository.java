package com.aisa.provider.repository;

import com.aisa.provider.model.ProviderUsageRecord;
import org.springframework.data.repository.Repository;

import java.time.Instant;
import java.util.List;

/**
 * Persistence port for {@link ProviderUsageRecord} (Requirement 20.8).
 *
 * <p>Provided here so the three provider entities each have a repository. The usage-recording
 * behaviour that writes these rows on each served request, and the retention pruning that
 * deletes rows older than the configured period, are implemented in task 7.3.
 */
public interface ProviderUsageRecordRepository extends Repository<ProviderUsageRecord, String> {

    ProviderUsageRecord save(ProviderUsageRecord record);

    List<ProviderUsageRecord> findByServedAtAfter(Instant cutoff);

    /**
     * Count usage records served at or after {@code cutoff}. Used to assert that records inside
     * the retention window are preserved (Req 20.8).
     */
    long countByServedAtGreaterThanEqual(Instant cutoff);

    /**
     * Prune records served before {@code cutoff}. The retention task derives {@code cutoff} from
     * the configured retention period, which is held at or above 90 days (Req 20.8).
     *
     * @return the number of pruned records
     */
    long deleteByServedAtBefore(Instant cutoff);
}
