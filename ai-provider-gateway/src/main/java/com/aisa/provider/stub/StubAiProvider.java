package com.aisa.provider.stub;

import com.aisa.provider.contract.AiProvider;
import com.aisa.provider.contract.TokenUsage;
import com.aisa.provider.contract.UniformMessage;
import com.aisa.provider.contract.UniformRequest;
import com.aisa.provider.contract.UniformResponse;
import com.aisa.provider.contract.UniformResponseChunk;
import com.aisa.provider.model.ProviderType;
import reactor.core.publisher.Flux;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * A deterministic, dependency-free {@link AiProvider} used in dev and tests (Requirement 20.1).
 *
 * <p>The stub never calls an external service. For a given {@link UniformRequest} it always
 * produces the same response, which makes it safe to reuse across the agent and end-to-end
 * tests added in later tasks (the same input always yields the same blueprint content). The
 * response echoes the last user message prefixed with the serving provider so callers can
 * assert which provider was selected.
 *
 * <p>Streaming splits the same content into whitespace-delimited chunks in order, terminated by
 * a final chunk with {@link UniformResponseChunk#last()} set to {@code true}. Concatenating the
 * deltas reproduces the {@code complete} content exactly.
 */
public class StubAiProvider implements AiProvider {

    private final ProviderType providerType;

    public StubAiProvider(ProviderType providerType) {
        this.providerType = Objects.requireNonNull(providerType, "providerType");
    }

    @Override
    public ProviderType providerType() {
        return providerType;
    }

    @Override
    public UniformResponse complete(UniformRequest request) {
        Objects.requireNonNull(request, "request");
        String content = render(request);
        return new UniformResponse(content, providerType, "STOP",
                new TokenUsage(promptSize(request), content.length()));
    }

    @Override
    public Flux<UniformResponseChunk> stream(UniformRequest request) {
        Objects.requireNonNull(request, "request");
        String content = render(request);
        List<UniformResponseChunk> chunks = new ArrayList<>();
        if (content.isEmpty()) {
            chunks.add(new UniformResponseChunk("", providerType, true));
            return Flux.fromIterable(chunks);
        }
        String[] words = content.split(" ");
        for (int i = 0; i < words.length; i++) {
            // Preserve the single-space separators so concatenation reproduces the content.
            String delta = i == 0 ? words[i] : " " + words[i];
            chunks.add(new UniformResponseChunk(delta, providerType, false));
        }
        chunks.add(new UniformResponseChunk("", providerType, true));
        return Flux.fromIterable(chunks);
    }

    /**
     * Deterministically render the response content for a request. Depends only on the request
     * contents and the provider type, so the same input always yields the same output.
     */
    private String render(UniformRequest request) {
        String lastUser = request.messages().stream()
                .filter(m -> m.role() == com.aisa.provider.contract.UniformRole.USER)
                .map(UniformMessage::content)
                .reduce((first, second) -> second)
                .orElseGet(() -> request.messages().get(request.messages().size() - 1).content());
        return "[STUB:" + providerType.name() + "] " + lastUser;
    }

    private long promptSize(UniformRequest request) {
        return request.messages().stream()
                .mapToLong(m -> m.content().length())
                .sum();
    }
}
