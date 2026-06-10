package com.aisa.provider.stub;

import com.aisa.provider.contract.UniformMessage;
import com.aisa.provider.contract.UniformOptions;
import com.aisa.provider.contract.UniformRequest;
import com.aisa.provider.contract.UniformResponse;
import com.aisa.provider.contract.UniformResponseChunk;
import com.aisa.provider.model.ProviderType;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Unit tests for the deterministic {@link StubAiProvider} reused across dev and later tests.
 */
class StubAiProviderTest {

    private final StubAiProvider stub = new StubAiProvider(ProviderType.OPENAI);

    @Test
    void completeIsDeterministicForSameInput() {
        UniformRequest request = UniformRequest.ofPrompt("hello world");

        UniformResponse first = stub.complete(request);
        UniformResponse second = stub.complete(request);

        assertEquals(first.content(), second.content());
        assertEquals("[STUB:OPENAI] hello world", first.content());
        assertEquals(ProviderType.OPENAI, first.servedBy());
    }

    @Test
    void completeEchoesLastUserMessage() {
        UniformRequest request = new UniformRequest(List.of(
                UniformMessage.system("be terse"),
                UniformMessage.user("first"),
                UniformMessage.assistant("ok"),
                UniformMessage.user("second")
        ), UniformOptions.defaults());

        assertEquals("[STUB:OPENAI] second", stub.complete(request).content());
    }

    @Test
    void streamChunksConcatenateToCompleteContent() {
        UniformRequest request = UniformRequest.ofPrompt("hello world");

        List<UniformResponseChunk> chunks = stub.stream(request).collectList().block();

        assertTrue(chunks != null && !chunks.isEmpty());
        // Terminal chunk is flagged last; preceding chunks are not.
        assertTrue(chunks.get(chunks.size() - 1).last());
        for (int i = 0; i < chunks.size() - 1; i++) {
            assertFalse(chunks.get(i).last());
        }
        String reconstructed = chunks.stream()
                .map(UniformResponseChunk::delta)
                .reduce("", String::concat);
        assertEquals(stub.complete(request).content(), reconstructed);
    }

    @Test
    void providerTypeMatchesConstruction() {
        assertEquals(ProviderType.CLAUDE, new StubAiProvider(ProviderType.CLAUDE).providerType());
    }
}
