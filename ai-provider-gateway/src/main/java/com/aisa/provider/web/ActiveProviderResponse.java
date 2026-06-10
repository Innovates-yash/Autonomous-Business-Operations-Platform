package com.aisa.provider.web;

import com.aisa.provider.model.ProviderType;

/**
 * The currently active AI provider (Requirement 20.2).
 *
 * @param activeProvider the active provider, or {@code null} when none has been selected
 * @param routable       whether a client is registered to actually serve the active provider
 */
public record ActiveProviderResponse(ProviderType activeProvider, boolean routable) {
}
