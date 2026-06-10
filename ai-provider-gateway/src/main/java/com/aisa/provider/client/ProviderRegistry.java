package com.aisa.provider.client;

import com.aisa.provider.contract.AiProvider;
import com.aisa.provider.model.ProviderType;
import com.aisa.provider.stub.StubAiProvider;
import org.springframework.stereotype.Component;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Resolves a {@link ProviderType} to the {@link AiProvider} client that serves it.
 *
 * <p>All {@link AiProvider} beans on the context are indexed by their {@link
 * AiProvider#providerType()}. When both a real client and a {@link StubAiProvider} are present
 * for the same provider (e.g. a misconfiguration), the real client wins so production routing is
 * never silently served by a stub.
 */
@Component
public class ProviderRegistry {

    private final Map<ProviderType, AiProvider> byType = new EnumMap<>(ProviderType.class);

    public ProviderRegistry(List<AiProvider> providers) {
        for (AiProvider provider : providers) {
            byType.merge(provider.providerType(), provider, ProviderRegistry::preferReal);
        }
    }

    private static AiProvider preferReal(AiProvider existing, AiProvider candidate) {
        boolean existingIsStub = existing instanceof StubAiProvider;
        boolean candidateIsStub = candidate instanceof StubAiProvider;
        if (existingIsStub && !candidateIsStub) {
            return candidate;
        }
        return existing;
    }

    /** @return the client for {@code type}, or empty when no client is registered for it. */
    public Optional<AiProvider> find(ProviderType type) {
        return Optional.ofNullable(byType.get(type));
    }

    /** @return {@code true} when a client is registered for {@code type}. */
    public boolean has(ProviderType type) {
        return byType.containsKey(type);
    }
}
