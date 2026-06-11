package com.aisa.auth.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

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
import java.lang.reflect.Field;
import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

/**
 * Unit tests for {@link OAuth2ExchangeService}: successful exchange creates user
 * and issues tokens (Requirement 1.8), exchange failure returns error without
 * tokens (Requirement 1.13), and existing OAuth identity links to existing user.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class OAuth2ExchangeServiceTest {

    private static final String SECRET = "test-secret-test-secret-test-secret-1234567890";
    private static final String PROVIDER = "google";
    private static final String CODE = "auth-code-123";
    private static final String REDIRECT_URI = "http://localhost:3000/callback";

    @Mock
    private OAuthProviderClient providerClient;

    @Mock
    private OAuthIdentityRepository oauthIdentityRepository;

    @Mock
    private UserRepository userRepository;

    @Mock
    private RoleRepository roleRepository;

    @Mock
    private RefreshTokenRepository refreshTokenRepository;

    private OAuth2ExchangeService service;

    @BeforeEach
    void setUp() {
        AuthTokenProperties tokenProperties = new AuthTokenProperties();
        tokenProperties.setSecret(SECRET);
        tokenProperties.setIssuer("aisa-auth");
        tokenProperties.setAccessTokenTtl(Duration.ofMinutes(15));
        tokenProperties.setRefreshTokenTtl(Duration.ofDays(7));

        JwtService jwtService = new JwtService(tokenProperties);

        service = new OAuth2ExchangeService(
                providerClient,
                oauthIdentityRepository,
                userRepository,
                roleRepository,
                refreshTokenRepository,
                jwtService,
                tokenProperties);
    }

    @Test
    void successfulExchangeCreatesNewUserAndIssuesTokens() {
        // Arrange: provider client returns user info
        when(providerClient.exchangeAndFetchUserInfo("google", CODE, REDIRECT_URI))
                .thenReturn(new OAuthProviderClient.OAuthUserInfo("user@example.com", "google-sub-123"));

        // No existing OAuth identity
        when(oauthIdentityRepository.findByProviderAndProviderUserId("google", "google-sub-123"))
                .thenReturn(Optional.empty());
        // No existing user with this email
        when(userRepository.findByEmail("user@example.com")).thenReturn(Optional.empty());

        Role guestRole = new Role(RoleName.GUEST);
        when(roleRepository.findByName(RoleName.GUEST)).thenReturn(Optional.of(guestRole));

        User savedUser = new User("user@example.com", null, guestRole);
        setId(savedUser, 99L);
        when(userRepository.save(any(User.class))).thenReturn(savedUser);
        when(oauthIdentityRepository.save(any(OAuthIdentity.class))).thenAnswer(inv -> inv.getArgument(0));
        when(refreshTokenRepository.save(any(RefreshToken.class))).thenAnswer(inv -> inv.getArgument(0));

        // Act
        TokenResponse response = service.exchange(PROVIDER, CODE, REDIRECT_URI);

        // Assert: tokens issued (Requirement 1.8)
        assertThat(response.accessToken()).isNotBlank();
        assertThat(response.refreshToken()).isNotBlank();
        assertThat(response.tokenType()).isEqualTo("Bearer");
        assertThat(response.expiresInSeconds()).isEqualTo(900L);

        // User was created with null passwordHash and GUEST role
        ArgumentCaptor<User> userCaptor = ArgumentCaptor.forClass(User.class);
        verify(userRepository).save(userCaptor.capture());
        User createdUser = userCaptor.getValue();
        assertThat(createdUser.getEmail()).isEqualTo("user@example.com");
        assertThat(createdUser.getPasswordHash()).isNull();
        assertThat(createdUser.getRole().getName()).isEqualTo(RoleName.GUEST);

        // OAuth identity was linked
        ArgumentCaptor<OAuthIdentity> identityCaptor = ArgumentCaptor.forClass(OAuthIdentity.class);
        verify(oauthIdentityRepository).save(identityCaptor.capture());
        OAuthIdentity identity = identityCaptor.getValue();
        assertThat(identity.getProvider()).isEqualTo("google");
        assertThat(identity.getProviderUserId()).isEqualTo("google-sub-123");

        // Refresh token was stored
        verify(refreshTokenRepository).save(any(RefreshToken.class));
    }

    @Test
    void exchangeFailureThrowsExceptionAndNoTokensIssued() {
        // Arrange: provider client throws on exchange failure
        when(providerClient.exchangeAndFetchUserInfo("google", CODE, REDIRECT_URI))
                .thenThrow(new OAuth2ExchangeException("OAuth2 token exchange failed for provider 'google'"));

        // Act & Assert (Requirement 1.13)
        assertThatThrownBy(() -> service.exchange(PROVIDER, CODE, REDIRECT_URI))
                .isInstanceOf(OAuth2ExchangeException.class)
                .hasMessageContaining("OAuth2 token exchange failed");

        // No tokens should be issued
        verify(refreshTokenRepository, never()).save(any());
        verify(userRepository, never()).save(any());
    }

    @Test
    void exchangeDeniedByProviderThrowsExceptionAndNoTokensIssued() {
        // Arrange: provider denies access
        when(providerClient.exchangeAndFetchUserInfo("google", CODE, REDIRECT_URI))
                .thenThrow(new OAuth2ExchangeException(
                        "OAuth2 token exchange denied by provider 'google': access_denied"));

        // Act & Assert (Requirement 1.13)
        assertThatThrownBy(() -> service.exchange(PROVIDER, CODE, REDIRECT_URI))
                .isInstanceOf(OAuth2ExchangeException.class)
                .hasMessageContaining("denied");

        verify(refreshTokenRepository, never()).save(any());
        verify(userRepository, never()).save(any());
    }

    @Test
    void existingOAuthIdentityLinksToExistingUser() {
        // Arrange
        when(providerClient.exchangeAndFetchUserInfo("google", CODE, REDIRECT_URI))
                .thenReturn(new OAuthProviderClient.OAuthUserInfo("existing@example.com", "google-sub-456"));

        Role clientRole = new Role(RoleName.CLIENT);
        User existingUser = new User("existing@example.com", "somehash", clientRole);
        setId(existingUser, 42L);

        OAuthIdentity existingIdentity = new OAuthIdentity(existingUser, "google", "google-sub-456", Instant.now());
        when(oauthIdentityRepository.findByProviderAndProviderUserId("google", "google-sub-456"))
                .thenReturn(Optional.of(existingIdentity));
        when(refreshTokenRepository.save(any(RefreshToken.class))).thenAnswer(inv -> inv.getArgument(0));

        // Act
        TokenResponse response = service.exchange(PROVIDER, CODE, REDIRECT_URI);

        // Assert: tokens issued for the existing user
        assertThat(response.accessToken()).isNotBlank();
        assertThat(response.refreshToken()).isNotBlank();
        assertThat(response.tokenType()).isEqualTo("Bearer");

        // No new user or identity should be created
        verify(userRepository, never()).save(any());
        verify(oauthIdentityRepository, never()).save(any());
    }

    @Test
    void existingEmailWithoutOAuthLinkCreatesIdentityOnly() {
        // Arrange: email exists but no OAuth link for this provider
        when(providerClient.exchangeAndFetchUserInfo("google", CODE, REDIRECT_URI))
                .thenReturn(new OAuthProviderClient.OAuthUserInfo("existing@example.com", "google-sub-789"));

        when(oauthIdentityRepository.findByProviderAndProviderUserId("google", "google-sub-789"))
                .thenReturn(Optional.empty());

        Role clientRole = new Role(RoleName.CLIENT);
        User existingUser = new User("existing@example.com", "somehash", clientRole);
        setId(existingUser, 55L);
        when(userRepository.findByEmail("existing@example.com")).thenReturn(Optional.of(existingUser));
        when(oauthIdentityRepository.save(any(OAuthIdentity.class))).thenAnswer(inv -> inv.getArgument(0));
        when(refreshTokenRepository.save(any(RefreshToken.class))).thenAnswer(inv -> inv.getArgument(0));

        // Act
        TokenResponse response = service.exchange(PROVIDER, CODE, REDIRECT_URI);

        // Assert
        assertThat(response.accessToken()).isNotBlank();

        // No new user created, but an identity link IS created
        verify(userRepository, never()).save(any());
        verify(oauthIdentityRepository).save(any(OAuthIdentity.class));
    }

    @Test
    void stubOAuthProviderClientWorksForDevTesting() {
        // Verify the stub implementation works as expected
        StubOAuthProviderClient stub = new StubOAuthProviderClient();

        // Register a success
        stub.registerSuccess("google", "test-code", "stub@example.com", "stub-subject-1");

        OAuthProviderClient.OAuthUserInfo result =
                stub.exchangeAndFetchUserInfo("google", "test-code", "http://localhost/callback");
        assertThat(result.email()).isEqualTo("stub@example.com");
        assertThat(result.subject()).isEqualTo("stub-subject-1");

        // Register a failure
        stub.registerFailure("google", "bad-code", "Access denied by provider");
        assertThatThrownBy(() -> stub.exchangeAndFetchUserInfo("google", "bad-code", "http://localhost/callback"))
                .isInstanceOf(OAuth2ExchangeException.class)
                .hasMessageContaining("Access denied by provider");

        // Unregistered code fails with default message
        assertThatThrownBy(() -> stub.exchangeAndFetchUserInfo("google", "unknown-code", "http://localhost/callback"))
                .isInstanceOf(OAuth2ExchangeException.class)
                .hasMessageContaining("no response registered");

        // Reset clears all
        stub.reset();
        assertThatThrownBy(() -> stub.exchangeAndFetchUserInfo("google", "test-code", "http://localhost/callback"))
                .isInstanceOf(OAuth2ExchangeException.class);
    }

    // ==================== Helper Methods ====================

    private static void setId(User user, long id) {
        try {
            Field field = User.class.getDeclaredField("id");
            field.setAccessible(true);
            field.set(user, id);
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException(e);
        }
    }
}
