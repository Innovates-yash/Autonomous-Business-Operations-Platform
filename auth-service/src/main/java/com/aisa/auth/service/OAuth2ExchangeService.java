package com.aisa.auth.service;

import com.aisa.auth.config.AuthTokenProperties;
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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Exchanges an OAuth2 authorization code with the external provider via a pluggable
 * {@link OAuthProviderClient}, retrieves the user's email and provider subject, links
 * or creates a User and OAuthIdentity record, and issues access + refresh tokens
 * within 10 seconds (Requirement 1.8).
 *
 * <p>On exchange failure or denial the service propagates the
 * {@link OAuth2ExchangeException} thrown by the provider client, ensuring no Platform
 * tokens are issued (Requirement 1.13).
 */
@Service
public class OAuth2ExchangeService {

    private static final Logger log = LoggerFactory.getLogger(OAuth2ExchangeService.class);
    private static final int REFRESH_TOKEN_BYTES = 32;

    private final OAuthProviderClient providerClient;
    private final OAuthIdentityRepository oauthIdentityRepository;
    private final UserRepository userRepository;
    private final RoleRepository roleRepository;
    private final RefreshTokenRepository refreshTokenRepository;
    private final JwtService jwtService;
    private final AuthTokenProperties tokenProperties;
    private final SecureRandom secureRandom = new SecureRandom();

    public OAuth2ExchangeService(
            OAuthProviderClient providerClient,
            OAuthIdentityRepository oauthIdentityRepository,
            UserRepository userRepository,
            RoleRepository roleRepository,
            RefreshTokenRepository refreshTokenRepository,
            JwtService jwtService,
            AuthTokenProperties tokenProperties) {
        this.providerClient = providerClient;
        this.oauthIdentityRepository = oauthIdentityRepository;
        this.userRepository = userRepository;
        this.roleRepository = roleRepository;
        this.refreshTokenRepository = refreshTokenRepository;
        this.jwtService = jwtService;
        this.tokenProperties = tokenProperties;
    }

    /**
     * Exchanges the authorization code with the configured provider via the pluggable
     * {@link OAuthProviderClient}, retrieves user info, links or creates a Platform
     * user, and issues tokens.
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

        // Step 1 & 2: Exchange code and fetch user info via pluggable client.
        OAuthProviderClient.OAuthUserInfo userInfo =
                providerClient.exchangeAndFetchUserInfo(normalizedProvider, code, redirectUri);

        // Step 3: Link or create the Platform user.
        User user = linkOrCreateUser(normalizedProvider, userInfo);

        // Step 4: Issue Platform tokens.
        return issueTokenPair(user);
    }

    /**
     * Links an existing OAuthIdentity to the user, or creates a new user and identity.
     * If the provider subject already has a linked identity, returns the associated user.
     * If the email already exists in the Platform but without an OAuth link for this
     * provider, links the new identity to the existing user.
     */
    private User linkOrCreateUser(String provider, OAuthProviderClient.OAuthUserInfo userInfo) {
        return oauthIdentityRepository
                .findByProviderAndProviderUserId(provider, userInfo.subject())
                .map(OAuthIdentity::getUser)
                .orElseGet(() -> createOrLinkUser(provider, userInfo));
    }

    private User createOrLinkUser(String provider, OAuthProviderClient.OAuthUserInfo userInfo) {
        Instant now = Instant.now();

        // Check if a user with this email already exists.
        User user = userRepository.findByEmail(userInfo.email()).orElse(null);

        if (user == null) {
            // Create a new user without a password (OAuth2-only account, GUEST role).
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
}
