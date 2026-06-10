package com.aisa.provider.repository;

import com.aisa.provider.model.ProviderConfig;
import com.aisa.provider.model.ProviderType;
import org.springframework.data.repository.Repository;

import java.util.List;
import java.util.Optional;

/**
 * Persistence port for {@link ProviderConfig} (Requirement 20.1).
 *
 * <p>Declared as a narrow Spring Data {@link Repository} exposing only the operations the
 * gateway needs. A provider is selectable only when a configuration row exists with
 * {@code configured = true} (Req 20.2/20.3); the {@code findByConfiguredTrue...} query also
 * backs fallback ordering by {@code fallbackPriority} (Req 20.6, used by task 7.3).
 */
public interface ProviderConfigRepository extends Repository<ProviderConfig, String> {

    ProviderConfig save(ProviderConfig config);

    Optional<ProviderConfig> findByProvider(ProviderType provider);

    List<ProviderConfig> findAll();

    List<ProviderConfig> findByConfiguredTrueOrderByFallbackPriorityAsc();
}
