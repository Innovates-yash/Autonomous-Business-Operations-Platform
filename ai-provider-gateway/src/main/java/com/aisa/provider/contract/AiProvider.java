package com.aisa.provider.contract;

import com.aisa.provider.model.ProviderType;
import reactor.core.publisher.Flux;

/**
 * The uniform contract every concrete provider client must satisfy (Requirement 20.4).
 *
 * <p>This is the single abstraction the gateway, agents, and chat service program against.
 * Implementations (added in task 7.2 — OpenAI, Gemini, Claude, Local LLM) adapt the uniform
 * {@link UniformRequest}/{@link UniformResponse} types to their underlying Spring AI
 * {@code ChatModel}. Because the request and response field sets are defined here and never
 * vary by provider, swapping providers is a configuration change, not a code change in any
 * caller.
 *
 * <p>Two operations are exposed:
 * <ul>
 *   <li>{@link #complete(UniformRequest)} — synchronous, returns the full response.</li>
 *   <li>{@link #stream(UniformRequest)} — returns an ordered stream of partial chunks.</li>
 * </ul>
 */
public interface AiProvider {

    /**
     * @return the provider this client routes to. Each implementation serves exactly one
     *         {@link ProviderType}.
     */
    ProviderType providerType();

    /**
     * Execute a blocking completion against the provider.
     *
     * @param request the provider-agnostic request; never {@code null}
     * @return the full provider-agnostic response
     */
    UniformResponse complete(UniformRequest request);

    /**
     * Execute a streaming completion against the provider.
     *
     * @param request the provider-agnostic request; never {@code null}
     * @return an ordered {@link Flux} of response chunks; the final chunk has
     *         {@link UniformResponseChunk#last()} set to {@code true}
     */
    Flux<UniformResponseChunk> stream(UniformRequest request);
}
