package com.aisa.provider.client;

import com.aisa.provider.contract.UniformMessage;
import com.aisa.provider.contract.UniformOptions;
import com.aisa.provider.contract.UniformRequest;
import com.aisa.provider.contract.UniformResponse;
import com.aisa.provider.contract.UniformResponseChunk;
import com.aisa.provider.model.ProviderType;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.metadata.ChatGenerationMetadata;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.prompt.ChatOptions;
import org.springframework.ai.chat.prompt.ChatOptionsBuilder;
import org.springframework.ai.chat.prompt.Prompt;
import reactor.core.publisher.Flux;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Unit tests for {@link SpringAiProvider} verifying the uniform-to-SpringAI mapping using a
 * hand-written fake {@link ChatModel} (no mocking framework).
 */
class SpringAiProviderTest {

    /** Captures the prompt it receives and returns a deterministic response. */
    private static final class FakeChatModel implements ChatModel {
        private Prompt lastPrompt;

        @Override
        public ChatResponse call(Prompt prompt) {
            this.lastPrompt = prompt;
            Generation generation = new Generation(new AssistantMessage("echo-response"))
                    .withGenerationMetadata(ChatGenerationMetadata.from("STOP", null));
            return new ChatResponse(List.of(generation));
        }

        @Override
        public Flux<ChatResponse> stream(Prompt prompt) {
            this.lastPrompt = prompt;
            return Flux.just(chunk("hel"), chunk("lo"));
        }

        @Override
        public ChatOptions getDefaultOptions() {
            return ChatOptionsBuilder.builder().build();
        }

        private static ChatResponse chunk(String text) {
            return new ChatResponse(List.of(new Generation(new AssistantMessage(text))));
        }
    }

    private final FakeChatModel chatModel = new FakeChatModel();
    private final OpenAiProvider provider = new OpenAiProvider(chatModel);

    @Test
    void completeMapsResponseAndStampsServingProvider() {
        UniformResponse response = provider.complete(UniformRequest.ofPrompt("hi"));

        assertEquals("echo-response", response.content());
        assertEquals(ProviderType.OPENAI, response.servedBy());
        assertEquals("STOP", response.finishReason());
    }

    @Test
    void completeTranslatesAllUniformMessagesIntoPrompt() {
        UniformRequest request = new UniformRequest(List.of(
                UniformMessage.system("sys"),
                UniformMessage.user("usr"),
                UniformMessage.assistant("asst")
        ), UniformOptions.defaults());

        provider.complete(request);

        List<Message> instructions = chatModel.lastPrompt.getInstructions();
        assertEquals(3, instructions.size());
        assertEquals("sys", instructions.get(0).getContent());
        assertEquals("usr", instructions.get(1).getContent());
        assertEquals("asst", instructions.get(2).getContent());
    }

    @Test
    void streamAppendsTerminalChunk() {
        List<UniformResponseChunk> chunks = provider.stream(UniformRequest.ofPrompt("hi"))
                .collectList().block();

        assertTrue(chunks != null && chunks.size() >= 3);
        assertTrue(chunks.get(chunks.size() - 1).last());
        String content = chunks.stream().map(UniformResponseChunk::delta).reduce("", String::concat);
        assertEquals("hello", content);
    }
}
