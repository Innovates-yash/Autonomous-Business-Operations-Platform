package com.aisa.auth.config;

import java.util.Map;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Configurable OAuth2 provider endpoints (Requirement 1.8). Each provider entry
 * specifies the token exchange URL and the user-info URL. The Platform supports
 * Google and GitHub out of the box; additional providers can be added via configuration.
 *
 * <p>Example configuration:
 * <pre>
 * aisa:
 *   auth:
 *     oauth2:
 *       providers:
 *         google:
 *           token-uri: https://oauth2.googleapis.com/token
 *           user-info-uri: https://www.googleapis.com/oauth2/v3/userinfo
 *           client-id: ...
 *           client-secret: ...
 *         github:
 *           token-uri: https://github.com/login/oauth/access_token
 *           user-info-uri: https://api.github.com/user
 *           client-id: ...
 *           client-secret: ...
 * </pre>
 */
@ConfigurationProperties(prefix = "aisa.auth.oauth2")
public class OAuth2ProviderProperties {

    private Map<String, ProviderConfig> providers = Map.of();

    public Map<String, ProviderConfig> getProviders() {
        return providers;
    }

    public void setProviders(Map<String, ProviderConfig> providers) {
        this.providers = providers;
    }

    /**
     * Configuration for a single OAuth2 provider.
     */
    public static class ProviderConfig {

        /** The provider's token exchange endpoint. */
        private String tokenUri;

        /** The provider's user-info endpoint. */
        private String userInfoUri;

        /** The OAuth2 client ID registered with this provider. */
        private String clientId;

        /** The OAuth2 client secret registered with this provider. */
        private String clientSecret;

        public String getTokenUri() {
            return tokenUri;
        }

        public void setTokenUri(String tokenUri) {
            this.tokenUri = tokenUri;
        }

        public String getUserInfoUri() {
            return userInfoUri;
        }

        public void setUserInfoUri(String userInfoUri) {
            this.userInfoUri = userInfoUri;
        }

        public String getClientId() {
            return clientId;
        }

        public void setClientId(String clientId) {
            this.clientId = clientId;
        }

        public String getClientSecret() {
            return clientSecret;
        }

        public void setClientSecret(String clientSecret) {
            this.clientSecret = clientSecret;
        }
    }
}
