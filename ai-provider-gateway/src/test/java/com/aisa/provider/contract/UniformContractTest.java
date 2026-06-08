package com.aisa.provider.contract;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Unit tests for the provider-agnostic uniform request/response contract (Requirement 20.4).
 */
class UniformContractTest {

    @Test
    void ofPromptBuildsSingleUserMessageWithDefaultOptions() {
        UniformRequest request = UniformRequest.ofPrompt("hello");

        assertEquals(1, request.messages().size());
        assertEquals(UniformRole.USER, request.messages().get(0).role());
        assertEquals("hello", request.messages().get(0).content());
        assertSame(UniformOptions.defaults(), request.options());
    }

    @Test
    void requestRejectsEmptyMessageList() {
        assertThrows(IllegalArgumentException.class,
                () -> new UniformRequest(List.of(), UniformOptions.defaults()));
    }

    @Test
    void requestDefaultsNullOptionsToDefaults() {
        UniformRequest request = new UniformRequest(List.of(UniformMessage.user("x")), null);
        assertSame(UniformOptions.defaults(), request.options());
    }

    @Test
    void requestMessagesAreImmutable() {
        List<UniformMessage> messages = new java.util.ArrayList<>();
        messages.add(UniformMessage.user("a"));
        UniformRequest request = new UniformRequest(messages, UniformOptions.defaults());

        // Mutating the source list does not affect the request (defensive copy).
        messages.add(UniformMessage.user("b"));
        assertEquals(1, request.messages().size());
        assertThrows(UnsupportedOperationException.class,
                () -> request.messages().add(UniformMessage.user("c")));
    }

    @Test
    void messageFactoriesSetExpectedRoles() {
        assertEquals(UniformRole.SYSTEM, UniformMessage.system("s").role());
        assertEquals(UniformRole.USER, UniformMessage.user("u").role());
        assertEquals(UniformRole.ASSISTANT, UniformMessage.assistant("a").role());
    }

    @Test
    void tokenUsageTotalsPromptAndCompletion() {
        TokenUsage usage = new TokenUsage(10, 5);
        assertEquals(15, usage.totalTokens());
        assertEquals(0, TokenUsage.NONE.totalTokens());
    }

    @Test
    void responseDefaultsNullUsageToNone() {
        UniformResponse response = new UniformResponse(
                "out", com.aisa.provider.model.ProviderType.OPENAI, "STOP", null);
        assertSame(TokenUsage.NONE, response.usage());
    }

    @Test
    void responseChunkRetainsDeltaAndTerminalFlag() {
        UniformResponseChunk chunk = new UniformResponseChunk(
                "tok", com.aisa.provider.model.ProviderType.CLAUDE, true);
        assertEquals("tok", chunk.delta());
        assertTrue(chunk.last());
    }
}
