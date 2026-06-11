package com.aisa.auth.service;

import com.aisa.auth.config.OAuth2ProviderProperties;
import com.aisa.auth.config.OAuth2ProviderProperties.ProviderConfig;
import java.util.Locale;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

/**
 * Production {@link OAuthProviderClient} that exchanges an authorization code and
 * retrieves user info over HTTP using Spring's {@link RestClient} (Requirement 1.8).
 *
 * <p>This implementation resolves provider configuration from
 * {@link OAuth2ProviderProperties}, calls the provider's token endpoint, and then
 * calls the user-info endpoint with the obtained access token. On any failure it
 * throws {@link OAuth2ExchangeException} (Requirement 1.13).
 */
public class RestClientOAuthProviderClient implements OAuthProviderClient {

    private static final Logger log = LoggerFactory.getLogger(RestClientOAuthProviderClient.class);

    private final OAuth2ProviderProperties providerProperties;
    private final RestClient restClient;

    public RestClientOAuthProviderClient(
            OAuth2ProviderProperties providerProperties,
            RestClient.Builder restClientBuilder) {
        this.providerProperties = providerProperties;
        this.restClient = restClientBuilder.build();
    }

    @Override
    public OAuthUserInfo exchangeAndFetchUserInfo(String provider, String code, String redirectUri) {
        String normalizedProvider = provider.trim().toLowerCase(Locale.ROOT);
        ProviderConfig config = resolveProviderConfig(normalizedProvider);

        String providerAccessToken = exchangeCodeForToken(config, code, redirectUri, normalizedProvider);
        return fetchUserInfo(config, providerAccessToken, normalizedProvider);
    }

    private ProviderConfig resolveProviderConfig(String provider) {
        Map<String, ProviderConfig> providers = providerProperties.getProviders();
        if (providers == null || !providers.containsKey(provider)) {
            throw new OAuth2ExchangeException(
                    "OAuth2 provider '" + provider + "' is not configured");
        }
        return providers.get(provider);
    }

    @SuppressWarnings("unchecked")
    private String exchangeCodeForToken(
            ProviderConfig config, String code, String redirectUri, String provider) {
        try {
            Map<String, Object> response = restClient.post()
                    .uri(config.getTokenUri())
                    .header("Accept", "application/json")
                    .body(Map.of(
                            "grant_type", "authorization_code",
                            "code", code,
                            "redirect_uri", redirectUri,
                            "client_id", config.getClientId(),
                            "client_secret", config.getClientSecret()))
                    .retrieve()
                    .body(Map.class);

            if (response == null || !response.containsKey("access_token")) {
                throw new OAuth2ExchangeException(
                        "OAuth2 token exchange failed for provider '" + provider + "': no access token in response");
            }

            if (response.containsKey("error")) {
                String error = String.valueOf(response.get("error"));
                throw new OAuth2ExchangeException(
                        "OAuth2 token exchange denied by provider '" + provider + "': " + error);
            }

            return String.valueOf(response.get("access_token"));
        } catch (RestClientException ex) {
            log.warn("OAuth2 token exchange failed for provider '{}': {}", provider, ex.getMessage());
            throw new OAuth2ExchangeException(
                    "OAuth2 token exchange failed for provider '" + provider + "'", ex);
        }
    }

    @SuppressWarnings("unchecked")
    private OAuthUserInfo fetchUserInfo(ProviderConfig config, String accessToken, String provider) {
        try {
            Map<String, Object> response = restClient.get()
                    .uri(config.getUserInfoUri())
                    .header("Authorization", "Bearer " + accessToken)
                    .header("Accept", "application/json")
                    .retrieve()
                    .body(Map.class);

            if (response == null) {
                throw new OAuth2ExchangeException(
                        "OAuth2 user info retrieval failed for provider '" + provider + "': empty response");
            }

            String email = extractEmail(response, provider);
            String subject = extractSubject(response, provider);

            return new OAuthUserInfo(email, subject);
        } catch (RestClientException ex) {
            log.warn("OAuth2 user info retrieval failed for provider '{}': {}", provider, ex.getMessage());
            throw new OAuth2ExchangeException(
                    "OAuth2 user info retrieval failed for provider '" + provider + "'", ex);
        }
    }

    private String extractEmail(Map<String, Object> response, String provider) {
        Object emailObj = response.get("email");
        if (emailObj == null || emailObj.toString().isBlank()) {
            throw new OAuth2ExchangeException(
                    "OAuth2 provider '" + provider + "' did not return an email address");
        }
        return emailObj.toString().trim().toLowerCase(Locale.ROOT);
    }

    private String extractSubject(Map<String, Object> response, String provider) {
        Object subObj = response.get("sub");
        if (subObj == null) {
            subObj = response.get("id");
        }
        if (subObj == null) {
            throw new OAuth2ExchangeException(
                    "OAuth2 provider '" + provider + "' did not return a subject identifier");
        }
        return String.valueOf(subObj);
    }
}
