package com.aisa.auth.service;

import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Stub {@link OAuthProviderClient} for development and testing environments.
 *
 * <p>Instead of calling an actual OAuth2 provider over HTTP, this stub allows
 * tests and local development to configure deterministic responses. Callers
 * can register provider responses or configure failures before invoking the
 * OAuth2 exchange flow.
 *
 * <p>By default, all exchanges fail with an {@link OAuth2ExchangeException} unless
 * a response has been registered for the given provider + code pair.
 */
public class StubOAuthProviderClient implements OAuthProviderClient {

    private final Map<String, OAuthUserInfo> responses = new ConcurrentHashMap<>();
    private final Map<String, OAuth2ExchangeException> failures = new ConcurrentHashMap<>();

    /**
     * Registers a successful response for the given provider and authorization code.
     *
     * @param provider the provider key (e.g. "google")
     * @param code     the authorization code that will trigger this response
     * @param email    the email to return
     * @param subject  the subject identifier to return
     */
    public void registerSuccess(String provider, String code, String email, String subject) {
        String key = buildKey(provider, code);
        responses.put(key, new OAuthUserInfo(email, subject));
        failures.remove(key);
    }

    /**
     * Registers a failure for the given provider and authorization code.
     *
     * @param provider the provider key
     * @param code     the authorization code that will trigger the failure
     * @param message  the error message
     */
    public void registerFailure(String provider, String code, String message) {
        String key = buildKey(provider, code);
        failures.put(key, new OAuth2ExchangeException(message));
        responses.remove(key);
    }

    /**
     * Clears all registered responses and failures.
     */
    public void reset() {
        responses.clear();
        failures.clear();
    }

    @Override
    public OAuthUserInfo exchangeAndFetchUserInfo(String provider, String code, String redirectUri) {
        String normalizedProvider = provider.trim().toLowerCase(Locale.ROOT);
        String key = buildKey(normalizedProvider, code);

        OAuth2ExchangeException failure = failures.get(key);
        if (failure != null) {
            throw new OAuth2ExchangeException(failure.getMessage());
        }

        OAuthUserInfo userInfo = responses.get(key);
        if (userInfo != null) {
            return userInfo;
        }

        // Default behavior: fail with "not configured" when no response is registered.
        throw new OAuth2ExchangeException(
                "Stub: no response registered for provider '" + normalizedProvider + "' with code '" + code + "'");
    }

    private static String buildKey(String provider, String code) {
        return provider.toLowerCase(Locale.ROOT) + ":" + code;
    }
}
