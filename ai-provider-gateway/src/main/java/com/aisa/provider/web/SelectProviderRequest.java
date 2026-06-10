package com.aisa.provider.web;

import com.aisa.provider.model.ProviderType;
import jakarta.validation.constraints.NotNull;

/**
 * Admin request to make a provider the active AI provider (Requirement 20.2).
 *
 * @param provider the provider to activate; must be one of the supported {@link ProviderType}s
 */
public record SelectProviderRequest(@NotNull ProviderType provider) {
}
