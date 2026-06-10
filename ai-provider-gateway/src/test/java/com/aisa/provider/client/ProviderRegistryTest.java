package com.aisa.provider.client;

import com.aisa.provider.contract.AiProvider;
import com.aisa.provider.contract.UniformRequest;
import com.aisa.provider.contract.UniformResponse;
import com.aisa.provider.contract.UniformResponseChunk;
import com.aisa.provider.model.ProviderType;
import com.aisa.provider.stub.StubAiProvider;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Flux;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Unit tests for {@link ProviderRegistry} resolution and the stub-vs-real preference.
 */
class ProviderRegistryTest {

    /** A minimal non-stub provider used to verify real clients win over stubs. */
    private static final class FakeRealProvider implements AiProvider {
        @Override
        public ProviderType providerType() {
            return ProviderType.OPENAI;
        }

        @Override
        public UniformResponse complete(UniformRequest request) {
            return new UniformResponse("real", ProviderType.OPENAI, "STOP", null);
        }

        @Override
        public Flux<UniformResponseChunk> stream(UniformRequest request) {
            return Flux.just(new UniformResponseChunk("real", ProviderType.OPENAI, true));
        }
    }

    @Test
    void resolvesRegisteredProviderByType() {
        StubAiProvider claude = new StubAiProvider(ProviderType.CLAUDE);
        ProviderRegistry registry = new ProviderRegistry(List.of(claude));

        assertTrue(registry.has(ProviderType.CLAUDE));
        assertSame(claude, registry.find(ProviderType.CLAUDE).orElseThrow());
        assertFalse(registry.has(ProviderType.OPENAI));
    }

    @Test
    void realProviderWinsOverStubForSameType() {
        FakeRealProvider real = new FakeRealProvider();
        StubAiProvider stub = new StubAiProvider(ProviderType.OPENAI);

        // Order should not matter: stub first then real, and real first then stub.
        ProviderRegistry stubFirst = new ProviderRegistry(List.of(stub, real));
        ProviderRegistry realFirst = new ProviderRegistry(List.of(real, stub));

        assertSame(real, stubFirst.find(ProviderType.OPENAI).orElseThrow());
        assertSame(real, realFirst.find(ProviderType.OPENAI).orElseThrow());
    }
}
