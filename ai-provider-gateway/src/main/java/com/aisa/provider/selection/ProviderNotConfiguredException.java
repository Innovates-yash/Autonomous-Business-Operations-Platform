package com.aisa.provider.selection;

import com.aisa.provider.model.ProviderType;

/**
 * Thrown when an Admin attempts to select a provider that is not configured (Requirement 20.3).
 *
 * <p>The gateway rejects the selection and retains the previously selected provider; this
 * exception carries the rejected provider so the API layer can return a clear error.
 */
public class ProviderNotConfiguredException extends RuntimeException {

    private final transient ProviderType provider;

    public ProviderNotConfiguredException(ProviderType provider) {
        super("Provider " + provider + " is not configured and cannot be selected");
        this.provider = provider;
    }

    public ProviderType getProvider() {
        return provider;
    }
}
