package com.aisa.auth.service;

import com.aisa.auth.config.AuthTokenProperties;
import com.aisa.auth.config.OAuth2ProviderProperties;
import com.aisa.auth.config.OAuth2ProviderProperties.ProviderConfig;
import com.aisa.auth.domain.OAuthIdentity;
import com.aisa.auth.domain.RefreshToken;
import com.aisa.auth.domain.Role;
import com.aisa.auth.domain.RoleName;
import com.aisa.auth.domain.User;
import com.aisa.auth.repository.OAuthIdentityRepository;
import com.aisa.auth.repository.RefreshTokenRepository;
import com.aisa.auth.repository.RoleRepository;
import com.aisa.auth.repository.UserRepository;
import com.aisa.auth.web.dto.TokenResponse;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Instant;
import java.util.Base64;
import java.util.Locale;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

/**
 * Exchanges an OAuth2 authorization code with the external provider, retrieves the
 * user's email and provider subject, links or creates a User and OAuthIdentity record,
 * and issues access + refresh tokens within 10 seconds (Requirement 1.8).
 *
 * <p>On exchange failure or denial the service throws {@link OAuth2ExchangeException}
 * ensuring no Platform tokens are issued (Requirement 1.13).
 */
@Service
public class OAuth2ExchangeService {

    private static final Logger log = LoggerFactory.getLogger(OAuth2ExchangeService.class);
    private static final int REFRESH_TOKEN_BYTES = 32;

    private final OAuth2ProviderProperties providerProperties;
    private final RestClient restClient;
    private final OAuthIdentityRepository oauthIdentityRepository;
    private final UserRepository userRepository;
    private final RoleRepository roleRepository;
    private final RefreshTokenRepository refreshTokenRepository;
    private final JwtService jwtService;
    private final AuthTokenProperties tokenProperties;
    private final SecureRandom secureRandom = new SecureRandom();

    public OAuth2ExchangeService(
            OAuth2ProviderProperties providerProperties,
            RestClient.Builder restClientBuilder,
            OAuthIdentityRepository oauthIdentityRepository,
            UserRepository userRepository,
            RoleRepository roleRepository,
            RefreshTokenRepository refreshTokenRepository,
            JwtService jwtService,
            AuthTokenProperties tokenProperties) {
        this.providerProperties = providerProperties;
        this.restClient = restClientBuilder.build();
        this.oauthIdentityRepository = oauthIdentityRepository;
        this.userRepository = userRepository;
        this.roleRepository = roleRepository;
        this.refreshTokenRepository = refreshTokenRepository;
        this.jwtService = jwtService;
        this.tokenProperties = tokenProperties;
    }

    /**
     * Exchanges the authorization code with the configured provider, retrieves user
     * info, links or creates a Platform user, and issues tokens.
     *
     * @param provider    the OAuth2 provider key (e.g. "google", "github")
     * @param code        the authorization code received from the provider
     * @param redirectUri the redirect URI used in the original authorization request
     * @return the issued access and refresh tokens
     * @throws OAuth2ExchangeException if the exchange fails or the provider denies it
     */
    @Transactional
    public TokenResponse exchange(String provider, String code, String redirectUri) {
        String normalizedProvider = provider.trim().toLowerCase(Locale.ROOT);

        ProviderConfig config = resolveProviderConfig(normalizedProvider);

        // Step 1: Exchange the authorization code for a provider access token.
        String providerAccessToken = exchangeCodeForToken(config, code, redirectUri, normalizedProvider);

        // Step 2: Retrieve user info (email and subject) from the provider.
        OAuth2UserInfo userInfo = fetchUserInfo(config, providerAccessToken, normalizedProvider);

        // Step 3: Link or create the Platform user.
        User user = linkOrCreateUser(normalizedProvider, userInfo);

        // Step 4: Issue Platform tokens.
        return issueTokenPair(user);
    }

    private ProviderConfig resolveProviderConfig(String provider) {
        Map<String, ProviderConfig> providers = providerProperties.getProviders();
        if (providers == null || !providers.containsKey(provider)) {
            throw new OAuth2ExchangeException(
                    "OAuth2 provider '" + provider + "' is not configured");
        }
        return providers.get(provider);
    }

    /**
     * Exchanges the authorization code for a provider access token using the
     * provider's token endpoint. The response shape differs by provider; this
     * implementation handles the standard OAuth2 token response with an
     * {@code access_token} field.
     */
    private String exchangeCodeForToken(
            ProviderConfig config, String code, String redirectUri, String provider) {
        try {
            @SuppressWarnings("unchecked")
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

            // Check for error response (some providers return 200 with error field).
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

    /**
     * Fetches user info (email, subject) from the provider's user-info endpoint
     * using the obtained access token.
     */
    private OAuth2UserInfo fetchUserInfo(ProviderConfig config, String accessToken, String provider) {
        try {
            @SuppressWarnings("unchecked")
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

            return new OAuth2UserInfo(email, subject);
        } catch (RestClientException ex) {
            log.warn("OAuth2 user info retrieval failed for provider '{}': {}", provider, ex.getMessage());
            throw new OAuth2ExchangeException(
                    "OAuth2 user info retrieval failed for provider '" + provider + "'", ex);
        }
    }

    private String extractEmail(Map<String, Object> response, String provider) {
        // Standard OIDC: "email" field. GitHub also uses "email".
        Object emailObj = response.get("email");
        if (emailObj == null || emailObj.toString().isBlank()) {
            throw new OAuth2ExchangeException(
                    "OAuth2 provider '" + provider + "' did not return an email address");
        }
        return emailObj.toString().trim().toLowerCase(Locale.ROOT);
    }

    private String extractSubject(Map<String, Object> response, String provider) {
        // Standard OIDC uses "sub"; GitHub uses "id" (numeric).
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

    /**
     * Links an existing OAuthIdentity to the user, or creates a new user and identity.
     * If the provider subject already has a linked identity, returns the associated user.
     * If the email already exists in the Platform but without an OAuth link for this
     * provider, links the new identity to the existing user.
     */
    private User linkOrCreateUser(String provider, OAuth2UserInfo userInfo) {
        // Check if OAuth identity already exists.
        return oauthIdentityRepository
                .findByProviderAndProviderUserId(provider, userInfo.subject())
                .map(OAuthIdentity::getUser)
                .orElseGet(() -> createOrLinkUser(provider, userInfo));
    }

    private User createOrLinkUser(String provider, OAuth2UserInfo userInfo) {
        Instant now = Instant.now();

        // Check if a user with this email already exists.
        User user = userRepository.findByEmail(userInfo.email()).orElse(null);

        if (user == null) {
            // Create a new user without a password (OAuth2-only account).
            Role guestRole = roleRepository.findByName(RoleName.GUEST)
                    .orElseThrow(() -> new IllegalStateException("Default GUEST role is not provisioned"));
            user = new User(userInfo.email(), null, guestRole);
            user = userRepository.save(user);
        }

        // Link the OAuth identity to the user.
        OAuthIdentity identity = new OAuthIdentity(user, provider, userInfo.subject(), now);
        oauthIdentityRepository.save(identity);

        return user;
    }

    private TokenResponse issueTokenPair(User user) {
        Instant now = Instant.now();
        String rawRefresh = generateRefreshTokenValue();
        String refreshHash = sha256Hex(rawRefresh);

        Instant expiresAt = now.plus(tokenProperties.getRefreshTokenTtl());
        RefreshToken refreshToken = new RefreshToken(user, refreshHash, now, expiresAt);
        refreshTokenRepository.save(refreshToken);

        IssuedAccessToken accessToken = jwtService.issue(user);
        return new TokenResponse(
                accessToken.token(),
                rawRefresh,
                "Bearer",
                accessToken.expiresInSeconds());
    }

    private String generateRefreshTokenValue() {
        byte[] bytes = new byte[REFRESH_TOKEN_BYTES];
        secureRandom.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    private static String sha256Hex(String value) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hashed = digest.digest(value.getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder(hashed.length * 2);
            for (byte b : hashed) {
                sb.append(Character.forDigit((b >> 4) & 0xF, 16));
                sb.append(Character.forDigit(b & 0xF, 16));
            }
            return sb.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 algorithm not available", e);
        }
    }

    /** Internal value object carrying the extracted user info from the provider. */
    record OAuth2UserInfo(String email, String subject) {
    }
}
