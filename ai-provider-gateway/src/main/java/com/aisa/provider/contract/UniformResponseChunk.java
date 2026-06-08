package com.aisa.provider.contract;

import com.aisa.provider.model.ProviderType;

import java.util.Objects;

/**
 * A single streamed fragment of a response (Requirement 20.4). A {@code stream} call emits
 * an ordered sequence of these chunks; concatenating every {@link #delta()} in order
 * reconstructs the full response content.
 *
 * @param delta    the incremental text for this chunk; never {@code null} (may be empty)
 * @param servedBy the provider producing the stream
 * @param last     {@code true} for the terminal chunk of the stream
 */
public record UniformResponseChunk(String delta, ProviderType servedBy, boolean last) {

    public UniformResponseChunk {
        Objects.requireNonNull(delta, "delta");
        Objects.requireNonNull(servedBy, "servedBy");
    }
}
