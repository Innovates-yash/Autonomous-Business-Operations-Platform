package com.aisa.provider.contract;

/**
 * Provider-agnostic generation options. Every field is optional; a {@code null} value means
 * "use the provider/model default". The option set is identical across providers so the
 * request contract does not vary by provider (Requirement 20.4).
 *
 * @param temperature   sampling temperature (typically 0.0–2.0), or {@code null} for default
 * @param maxTokens     maximum tokens to generate, or {@code null} for default
 * @param topP          nucleus sampling probability mass, or {@code null} for default
 */
public record UniformOptions(Double temperature, Integer maxTokens, Double topP) {

    private static final UniformOptions DEFAULTS = new UniformOptions(null, null, null);

    /** @return an options instance carrying no overrides (all provider defaults). */
    public static UniformOptions defaults() {
        return DEFAULTS;
    }
}
