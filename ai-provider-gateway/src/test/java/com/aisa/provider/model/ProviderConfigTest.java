package com.aisa.provider.model;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Unit tests for {@link ProviderConfig} timeout invariants (Requirement 20.5).
 */
class ProviderConfigTest {

    private ProviderConfig configWithTimeout(int seconds) {
        return new ProviderConfig(ProviderType.OPENAI, "OpenAI", "gpt-4o", true, seconds, null);
    }

    @Test
    void acceptsTimeoutWithinRange() {
        assertEquals(30, configWithTimeout(30).getRequestTimeoutSeconds());
        assertEquals(1, configWithTimeout(1).getRequestTimeoutSeconds());
        assertEquals(120, configWithTimeout(120).getRequestTimeoutSeconds());
    }

    @Test
    void rejectsTimeoutBelowMinimum() {
        assertThrows(IllegalArgumentException.class, () -> configWithTimeout(0));
    }

    @Test
    void rejectsTimeoutAboveMaximum() {
        assertThrows(IllegalArgumentException.class, () -> configWithTimeout(121));
    }

    @Test
    void generatesIdAndTimestamps() {
        ProviderConfig config = configWithTimeout(30);
        assertTrue(config.getId() != null && !config.getId().isBlank());
        assertEquals(config.getCreatedAt(), config.getCreatedAt());
        assertTrue(config.getUpdatedAt() != null);
    }
}
