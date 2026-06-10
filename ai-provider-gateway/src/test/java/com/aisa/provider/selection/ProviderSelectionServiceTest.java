package com.aisa.provider.selection;

import com.aisa.provider.client.ProviderRegistry;
import com.aisa.provider.model.ProviderConfig;
import com.aisa.provider.model.ProviderType;
import com.aisa.provider.stub.StubAiProvider;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Unit tests for {@link ProviderSelectionService} covering selection, rejection-with-retention,
 * and cache refresh (Requirements 20.2, 20.3).
 */
class ProviderSelectionServiceTest {

    private InMemoryProviderConfigRepository configs;
    private InMemoryProviderSelectionRepository selections;
    private ProviderSelectionService service;

    @BeforeEach
    void setUp() {
        configs = new InMemoryProviderConfigRepository();
        selections = new InMemoryProviderSelectionRepository();
        // OpenAI and Claude are configured; Gemini exists but is NOT configured; Local has no row.
        configs.save(new ProviderConfig(ProviderType.OPENAI, "OpenAI", "gpt-4o", true, 30, 0));
        configs.save(new ProviderConfig(ProviderType.CLAUDE, "Claude", "claude-3-5", true, 30, 1));
        configs.save(new ProviderConfig(ProviderType.GEMINI, "Gemini", "gemini-1.5", false, 30, 2));

        ProviderRegistry registry = new ProviderRegistry(List.of(
                new StubAiProvider(ProviderType.OPENAI),
                new StubAiProvider(ProviderType.CLAUDE),
                new StubAiProvider(ProviderType.GEMINI),
                new StubAiProvider(ProviderType.LOCAL_LLM)));
        service = new ProviderSelectionService(configs, selections, registry);
        service.loadInitialSelection();
    }

    @Test
    void selectingConfiguredProviderActivatesItAndRecordsHistory() {
        ProviderType active = service.selectProvider(ProviderType.OPENAI, "admin-1");

        assertEquals(ProviderType.OPENAI, active);
        assertEquals(ProviderType.OPENAI, service.currentSelection().orElseThrow());
        assertEquals(1, selections.size());
        assertEquals(1, service.selectionHistory().size());
        assertTrue(service.activeClient().isPresent());
    }

    @Test
    void selectingUnconfiguredProviderIsRejectedAndRetainsPrior() {
        service.selectProvider(ProviderType.OPENAI, "admin-1");

        // Gemini exists but is not configured → reject, retain OpenAI (Req 20.3).
        assertThrows(ProviderNotConfiguredException.class,
                () -> service.selectProvider(ProviderType.GEMINI, "admin-2"));

        assertEquals(ProviderType.OPENAI, service.currentSelection().orElseThrow());
        assertEquals(1, selections.size());
    }

    @Test
    void selectingProviderWithNoConfigRowIsRejected() {
        // LOCAL_LLM has no configuration row at all.
        assertThrows(ProviderNotConfiguredException.class,
                () -> service.selectProvider(ProviderType.LOCAL_LLM, "admin-1"));
        assertTrue(service.currentSelection().isEmpty());
        assertEquals(0, selections.size());
    }

    @Test
    void rejectedSelectionWithNoPriorLeavesNothingActive() {
        assertThrows(ProviderNotConfiguredException.class,
                () -> service.selectProvider(ProviderType.GEMINI, "admin-1"));
        assertTrue(service.currentSelection().isEmpty());
    }

    @Test
    void switchingBetweenConfiguredProvidersUpdatesActiveAndAppendsHistory() {
        service.selectProvider(ProviderType.OPENAI, "admin-1");
        ProviderType active = service.selectProvider(ProviderType.CLAUDE, "admin-2");

        assertEquals(ProviderType.CLAUDE, active);
        assertEquals(ProviderType.CLAUDE, service.currentSelection().orElseThrow());
        assertEquals(2, selections.size());
    }

    @Test
    void refreshAdoptsLatestPersistedSelection() {
        // Simulate another instance saving a selection directly to the store.
        selections.save(new com.aisa.provider.model.ProviderSelection(ProviderType.CLAUDE, "other"));

        service.refreshActiveSelection();

        assertEquals(ProviderType.CLAUDE, service.currentSelection().orElseThrow());
    }

    @Test
    void refreshIgnoresSelectionWhoseProviderIsNoLongerConfigured() {
        selections.save(new com.aisa.provider.model.ProviderSelection(ProviderType.GEMINI, "other"));

        service.refreshActiveSelection();

        // Gemini is not configured, so it must not become active (Req 20.3 spirit).
        assertFalse(service.currentSelection().isPresent());
    }
}
