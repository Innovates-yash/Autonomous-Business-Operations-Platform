package com.aisa.auth.config;

import com.aisa.auth.service.OAuthProviderClient;
import com.aisa.auth.service.RestClientOAuthProviderClient;
import com.aisa.auth.service.StubOAuthProviderClient;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestClient;

/**
 * Configures the active {@link OAuthProviderClient} bean based on the
 * {@code aisa.auth.oauth2.stub-enabled} property.
 *
 * <p>When {@code stub-enabled=true} (dev/test profiles), a
 * {@link StubOAuthProviderClient} is wired, allowing deterministic testing
 * without external provider connectivity. In production, the
 * {@link RestClientOAuthProviderClient} calls real provider endpoints.
 */
@Configuration
public class OAuthClientConfiguration {

    @Bean
    @ConditionalOnProperty(name = "aisa.auth.oauth2.stub-enabled", havingValue = "true")
    public OAuthProviderClient stubOAuthProviderClient() {
        return new StubOAuthProviderClient();
    }

    @Bean
    @ConditionalOnProperty(name = "aisa.auth.oauth2.stub-enabled", havingValue = "false", matchIfMissing = true)
    public OAuthProviderClient restClientOAuthProviderClient(
            OAuth2ProviderProperties providerProperties,
            RestClient.Builder restClientBuilder) {
        return new RestClientOAuthProviderClient(providerProperties, restClientBuilder);
    }
}
