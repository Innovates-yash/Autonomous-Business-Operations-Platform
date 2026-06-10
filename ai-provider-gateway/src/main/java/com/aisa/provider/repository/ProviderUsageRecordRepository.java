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
}
