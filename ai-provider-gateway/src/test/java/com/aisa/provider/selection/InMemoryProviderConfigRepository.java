package com.aisa.provider.selection;

import com.aisa.provider.model.ProviderConfig;
import com.aisa.provider.model.ProviderType;
import com.aisa.provider.repository.ProviderConfigRepository;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * A real, hand-written in-memory {@link ProviderConfigRepository} for unit tests — no mocking
 * framework. Behaves like the persistent store for the narrow query surface used by the
 * selection service.
 */
class InMemoryProviderConfigRepository implements ProviderConfigRepository {

    private final Map<ProviderType, ProviderConfig> byProvider = new EnumMap<>(ProviderType.class);

    @Override
    public ProviderConfig save(ProviderConfig config) {
        byProvider.put(config.getProvider(), config);
        return config;
    }

    @Override
    public Optional<ProviderConfig> findByProvider(ProviderType provider) {
        return Optional.ofNullable(byProvider.get(provider));
    }

    @Override
    public List<ProviderConfig> findAll() {
        return new ArrayList<>(byProvider.values());
    }

    @Override
    public List<ProviderConfig> findByConfiguredTrueOrderByFallbackPriorityAsc() {
        return byProvider.values().stream()
                .filter(ProviderConfig::isConfigured)
                .sorted(Comparator.comparing(c -> c.getFallbackPriority() == null
                        ? Integer.MAX_VALUE : c.getFallbackPriority()))
                .toList();
    }
}
