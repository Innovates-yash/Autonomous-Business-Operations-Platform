package com.aisa.provider.web;

import com.aisa.provider.model.ProviderConfig;
import com.aisa.provider.model.ProviderType;

/**
 * Admin-facing view of a configured provider (Requirement 20.1). Excludes secrets; credentials
 * are supplied via environment configuration and never returned by the API.
 *
 * @param provider          the provider this configuration describes
 * @param displayName       human-readable label
 * @param model             the target model identifier
 * @param configured        whether the provider is selectable (Req 20.2/20.3)
 * @param requestTimeoutSec per-request timeout in seconds (Req 20.5)
 * @param fallbackPriority  fallback ordering, lowest first, or {@code null} (Req 20.6)
 */
public record ProviderConfigView(
        ProviderType provider,
        String displayName,
        String model,
        boolean configured,
        int requestTimeoutSec,
        Integer fallbackPriority
) {
    public static ProviderConfigView from(ProviderConfig config) {
        return new ProviderConfigView(
                config.getProvider(),
                config.getDisplayName(),
                config.getModel(),
                config.isConfigured(),
                config.getRequestTimeoutSeconds(),
                config.getFallbackPriority());
    }
}
