package com.aisa.provider.config;

import com.aisa.provider.model.ProviderType;
import com.aisa.provider.stub.StubAiProvider;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Registers a deterministic {@link StubAiProvider} for every {@link ProviderType}.
 *
 * <p>Enabled by default (and explicitly in dev/test) because the real provider clients require
 * external API credentials and network access. The stubs guarantee the gateway always has a
 * routable client for each provider so selection and downstream agent/E2E tests are
 * deterministic. Disable with {@code aisa.provider.stub.enabled=false} in environments where
 * only real providers should be reachable.
 */
@Configuration
@ConditionalOnProperty(name = "aisa.provider.stub.enabled", havingValue = "true", matchIfMissing = true)
public class StubProviderConfiguration {

    @Bean
    public StubAiProvider openAiStub() {
        return new StubAiProvider(ProviderType.OPENAI);
    }

    @Bean
    public StubAiProvider geminiStub() {
        return new StubAiProvider(ProviderType.GEMINI);
    }

    @Bean
    public StubAiProvider claudeStub() {
        return new StubAiProvider(ProviderType.CLAUDE);
    }

    @Bean
    public StubAiProvider localLlmStub() {
        return new StubAiProvider(ProviderType.LOCAL_LLM);
    }
}
