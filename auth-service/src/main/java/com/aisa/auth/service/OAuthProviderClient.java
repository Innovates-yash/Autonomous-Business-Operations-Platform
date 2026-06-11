package com.aisa.auth.service;

/**
 * Pluggable abstraction for OAuth2 provider interactions (Requirement 1.8).
 *
 * <p>Implementations exchange an authorization code for user information from an
 * external OAuth2 provider. The Platform ships a production implementation backed
 * by {@link org.springframework.web.client.RestClient} and a stub implementation
 * for development and testing environments.
 *
 * <p>On exchange failure or user denial, implementations MUST throw
 * {@link OAuth2ExchangeException} so that the service layer can enforce the
 * "no tokens issued on failure" guarantee (Requirement 1.13).
 */
public interface OAuthProviderClient {

    /**
     * Exchanges an OAuth2 authorization code with the configured provider and
     * retrieves the authenticated user's info (email and provider-subject).
     *
     * @param provider    the OAuth2 provider key (e.g. "google", "github")
     * @param code        the authorization code received from the provider's redirect
     * @param redirectUri the redirect URI used in the original authorization request
     * @return the user's email and provider subject identifier
     * @throws OAuth2ExchangeException if the exchange fails, is denied, or user info
     *                                 cannot be retrieved
     */
    OAuthUserInfo exchangeAndFetchUserInfo(String provider, String code, String redirectUri);

    /**
     * User information returned by the OAuth2 provider after a successful
     * authorization-code exchange.
     *
     * @param email   the user's email address from the provider
     * @param subject the provider's unique subject identifier for this user
     */
    record OAuthUserInfo(String email, String subject) {
    }
}
