package com.aisa.provider.client;

import com.aisa.provider.contract.AiProvider;
import com.aisa.provider.contract.TokenUsage;
import com.aisa.provider.contract.UniformMessage;
import com.aisa.provider.contract.UniformOptions;
import com.aisa.provider.contract.UniformRequest;
import com.aisa.provider.contract.UniformResponse;
import com.aisa.provider.contract.UniformResponseChunk;
import com.aisa.provider.model.ProviderType;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.prompt.ChatOptions;
import org.springframework.ai.chat.prompt.ChatOptionsBuilder;
import org.springframework.ai.chat.prompt.Prompt;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.List;
import java.util.Objects;

/**
 * Base {@link AiProvider} implementation that adapts the uniform contract (Requirement 20.4)
 * onto a Spring AI {@link ChatModel}. Concrete providers (OpenAI, Gemini, Claude, Local LLM)
 * subclass this and supply only their {@link ProviderType}; all request/response translation
 * lives here so the contract is identical across providers.
 *
 * <p>This adapter performs no failover, timeout, or usage recording — those concerns are added
 * in task 7.3. It simply forwards a single uniform request to the wrapped model and maps the
 * result back to the uniform response/chunk types.
 */
public abstract class SpringAiProvider implements AiProvider {

    private final ChatModel chatModel;

    protected SpringAiProvider(ChatModel chatModel) {
        this.chatModel = Objects.requireNonNull(chatModel, "chatModel");
    }

    @Override
    public UniformResponse complete(UniformRequest request) {
        Objects.requireNonNull(request, "request");
        ChatResponse response = chatModel.call(toPrompt(request));
        Generation generation = response.getResult();
        String content = generation != null && generation.getOutput() != null
                ? generation.getOutput().getContent() : "";
        String finishReason = generation != null && generation.getMetadata() != null
                ? generation.getMetadata().getFinishReason() : null;
        return new UniformResponse(
                content == null ? "" : content,
                providerType(),
                finishReason,
                TokenUsage.NONE);
    }

    @Override
    public Flux<UniformResponseChunk> stream(UniformRequest request) {
        Objects.requireNonNull(request, "request");
        ProviderType type = providerType();
        return chatModel.stream(toPrompt(request))
                .map(chunk -> {
                    Generation generation = chunk.getResult();
                    String delta = generation != null && generation.getOutput() != null
                            ? generation.getOutput().getContent() : "";
                    return new UniformResponseChunk(delta == null ? "" : delta, type, false);
                })
                .concatWith(Mono.just(new UniformResponseChunk("", type, true)));
    }

    private Prompt toPrompt(UniformRequest request) {
        List<Message> messages = request.messages().stream()
                .map(SpringAiProvider::toMessage)
                .toList();
        return new Prompt(messages, toOptions(request.options()));
    }

    private static Message toMessage(UniformMessage message) {
        return switch (message.role()) {
            case SYSTEM -> new SystemMessage(message.content());
            case USER -> new UserMessage(message.content());
            case ASSISTANT -> new AssistantMessage(message.content());
        };
    }

    private static ChatOptions toOptions(UniformOptions options) {
        ChatOptionsBuilder builder = ChatOptionsBuilder.builder();
        if (options.temperature() != null) {
            builder.withTemperature(options.temperature());
        }
        if (options.maxTokens() != null) {
            builder.withMaxTokens(options.maxTokens());
        }
        if (options.topP() != null) {
            builder.withTopP(options.topP());
        }
        return builder.build();
    }
}
