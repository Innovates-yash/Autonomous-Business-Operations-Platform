package com.aisa.provider.contract;

import com.aisa.provider.model.ProviderType;

import java.util.Objects;

/**
 * The single, provider-agnostic response returned for a {@code complete} call
 * (Requirement 20.4). The response field set is identical for every provider; the
 * {@code servedBy} field records which provider actually produced the content, which also
 * supports usage recording (Requirement 20.8) without changing the contract shape.
 *
 * @param content      the generated text; never {@code null}
 * @param servedBy     the provider that produced this response
 * @param finishReason a normalized completion reason (e.g. {@code STOP}, {@code LENGTH})
 * @param usage        token accounting; never {@code null}
 */
public record UniformResponse(
        String content,
        ProviderType servedBy,
        String finishReason,
        TokenUsage usage
) {
    public UniformResponse {
        Objects.requireNonNull(content, "content");
        Objects.requireNonNull(servedBy, "servedBy");
        usage = usage == null ? TokenUsage.NONE : usage;
    }
}
