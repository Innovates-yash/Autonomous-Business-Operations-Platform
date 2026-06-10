package com.aisa.provider.selection;

import com.aisa.provider.contract.AiProvider;
import com.aisa.provider.client.ProviderRegistry;
import com.aisa.provider.model.ProviderConfig;
import com.aisa.provider.model.ProviderSelection;
import com.aisa.provider.model.ProviderType;
import com.aisa.provider.repository.ProviderConfigRepository;
import com.aisa.provider.repository.ProviderSelectionRepository;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Owns the active-provider selection (Requirement 20.2, 20.3).
 *
 * <p>The active provider is held in an in-memory cache so requests on this instance route to it
 * immediately after a selection is saved. A periodic {@link #refreshActiveSelection()} re-reads
 * the most recent persisted selection so other stateless instances converge to a new selection
 * within 5 seconds of it being saved (Req 20.2).
 *
 * <p>Selecting an unconfigured provider is rejected with {@link ProviderNotConfiguredException}
 * and nothing is persisted, so the previously selected provider is retained (Req 20.3). This
 * service performs no failover or usage recording (task 7.3).
 */
@Service
public class ProviderSelectionService {

    private static final Logger log = LoggerFactory.getLogger(ProviderSelectionService.class);

    private final ProviderConfigRepository configRepository;
    private final ProviderSelectionRepository selectionRepository;
    private final ProviderRegistry providerRegistry;

    /** The active provider for this instance; {@code null} until one is selected. */
    private final AtomicReference<ProviderType> activeProvider = new AtomicReference<>();

    public ProviderSelectionService(ProviderConfigRepository configRepository,
                                    ProviderSelectionRepository selectionRepository,
                                    ProviderRegistry providerRegistry) {
        this.configRepository = configRepository;
        this.selectionRepository = selectionRepository;
        this.providerRegistry = providerRegistry;
    }

    /** Load the persisted active selection on startup so routing survives a restart. */
    @PostConstruct
    public void loadInitialSelection() {
        refreshActiveSelection();
    }

    /**
     * Select the active provider (Requirement 20.2, 20.3).
     *
     * @param provider   the provider to activate
     * @param selectedBy identifier of the Admin making the selection
     * @return the now-active provider
     * @throws ProviderNotConfiguredException if {@code provider} is not configured; the prior
     *                                        selection is retained and nothing is persisted
     */
    public ProviderType selectProvider(ProviderType provider, String selectedBy) {
        if (provider == null) {
            throw new IllegalArgumentException("provider must not be null");
        }
        if (!isConfigured(provider)) {
            // Reject and retain the previously selected provider (Req 20.3).
            log.warn("Rejected selection of unconfigured provider {}; retaining {}",
                    provider, activeProvider.get());
            throw new ProviderNotConfiguredException(provider);
        }
        // Append to the selection history; the newest row is the active selection (Req 20.2).
        selectionRepository.save(new ProviderSelection(provider, selectedBy));
        activeProvider.set(provider);
        log.info("Active AI provider selected: {} (by {})", provider, selectedBy);
        return provider;
    }

    /** @return the active provider for this instance, or empty if none has been selected. */
    public Optional<ProviderType> currentSelection() {
        return Optional.ofNullable(activeProvider.get());
    }

    /**
     * @return the {@link AiProvider} client for the active selection, or empty when no provider
     *         is selected or no client is registered for it.
     */
    public Optional<AiProvider> activeClient() {
        ProviderType current = activeProvider.get();
        if (current == null) {
            return Optional.empty();
        }
        return providerRegistry.find(current);
    }

    /** @return the full selection history, most recent first (Req 20.2). */
    public List<ProviderSelection> selectionHistory() {
        return selectionRepository.findAllByOrderBySelectedAtDesc();
    }

    /**
     * Re-read the most recent persisted selection into the in-memory cache. Scheduled to run
     * frequently enough that a selection saved on another instance takes effect within 5
     * seconds (Req 20.2). A selection is only adopted while its provider remains configured.
     */
    @Scheduled(fixedDelayString = "${aisa.provider.selection.refresh-ms:2000}")
    public void refreshActiveSelection() {
        selectionRepository.findTopByOrderBySelectedAtDesc().ifPresent(selection -> {
            ProviderType persisted = selection.getSelectedProvider();
            if (isConfigured(persisted)) {
                activeProvider.set(persisted);
            }
        });
    }

    private boolean isConfigured(ProviderType provider) {
        return configRepository.findByProvider(provider)
                .map(ProviderConfig::isConfigured)
                .orElse(false);
    }
}
