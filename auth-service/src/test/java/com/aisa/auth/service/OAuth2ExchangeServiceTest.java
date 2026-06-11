package com.aisa.auth.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

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
import java.lang.reflect.Field;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.ResponseEntity;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

/**
 * Unit tests for {@link OAuth2ExchangeService}: successful exchange creates user
 * and issues tokens (Requirement 1.8), exchange failure returns error without
 * tokens (Requirement 1.13), and existing OAuth identity links to existing user.
 */
@ExtendWith(MockitoExtension.class)
class OAuth2ExchangeServiceTest {

    private static final String SECRET = "test-secret-test-secret-test-secret-1234567890";
    private static final String PROVIDER = "google";
    private static final String CODE = "auth-code-123";
    private static final String REDIRECT_URI = "http://localhost:3000/callback";

    @Mock
    private OAuthIdentityRepository oauthIdentityRepository;

    @Mock
    private UserRepository userRepository;

    @Mock
    private RoleRepository roleRepository;

    @Mock
    private RefreshTokenRepository refreshTokenRepository;

    @Mock
    private RestClient.Builder restClientBuilder;

    @Mock
    private RestClient restClient;

    @Mock
    private RestClient.RequestBodyUriSpec requestBodyUriSpec;

    @Mock
    private RestClient.RequestBodySpec requestBodySpec;

    @Mock
    private RestClient.RequestHeadersUriSpec<?> requestHeadersUriSpec;

    @Mock
    private RestClient.RequestHeadersSpec<?> requestHeadersSpec;

    @Mock
    private RestClient.ResponseSpec responseSpec;

    private OAuth2ExchangeService service;
    private OAuth2ProviderProperties providerProperties;

    @BeforeEach
    void setUp() {
        AuthTokenProperties tokenProperties = new AuthTokenProperties();
        tokenProperties.setSecret(SECRET);
        tokenProperties.setIssuer("aisa-auth");
        tokenProperties.setAccessTokenTtl(Duration.ofMinutes(15));
        tokenProperties.setRefreshTokenTtl(Duration.ofDays(7));

        JwtService jwtService = new JwtService(tokenProperties);

        ProviderConfig googleConfig = new ProviderConfig();
        googleConfig.setTokenUri("https://oauth2.googleapis.com/token");
        googleConfig.setUserInfoUri("https://www.googleapis.com/oauth2/v3/userinfo");
        googleConfig.setClientId("google-client-id");
        googleConfig.setClientSecret("google-client-secret");

        providerProperties = new OAuth2ProviderProperties();
        providerProperties.setProviders(Map.of("google", googleConfig));

        when(restClientBuilder.build()).thenReturn(restClient);

        service = new OAuth2ExchangeService(
                providerProperties,
                restClientBuilder,
                oauthIdentityRepository,
                userRepository,
                roleRepository,
                refreshTokenRepository,
                jwtService,
                tokenProperties);
    }

    @Test
    void successfulExchangeCreatesNewUserAndIssuesTokens() {
        // Arrange: mock the token exchange
        setupTokenExchangeSuccess();
        // Mock user info response
        setupUserInfoSuccess("user@example.com", "google-sub-123");

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

        // User was created
        ArgumentCaptor<User> userCaptor = ArgumentCaptor.forClass(User.class);
        verify(userRepository).save(userCaptor.capture());
        User createdUser = userCaptor.getValue();
        assertThat(createdUser.getEmail()).isEqualTo("user@example.com");
        assertThat(createdUser.getPasswordHash()).isNull(); // OAuth2-only account

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
        // Arrange: token exchange fails with network error
        setupTokenExchangeFailure(new RestClientException("Connection refused"));

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
        // Arrange: provider returns an error response
        setupTokenExchangeDenied();

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
        setupTokenExchangeSuccess();
        setupUserInfoSuccess("existing@example.com", "google-sub-456");

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
        setupTokenExchangeSuccess();
        setupUserInfoSuccess("existing@example.com", "google-sub-789");

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
    void unconfiguredProviderThrowsExchangeException() {
        // Act & Assert
        assertThatThrownBy(() -> service.exchange("unknown-provider", CODE, REDIRECT_URI))
                .isInstanceOf(OAuth2ExchangeException.class)
                .hasMessageContaining("not configured");

        verify(refreshTokenRepository, never()).save(any());
    }

    @Test
    void userInfoWithoutEmailThrowsExchangeException() {
        // Arrange
        setupTokenExchangeSuccess();
        // User info returns no email
        setupUserInfoResponseMap(Map.of("sub", "google-sub-noemail"));

        // Act & Assert
        assertThatThrownBy(() -> service.exchange(PROVIDER, CODE, REDIRECT_URI))
                .isInstanceOf(OAuth2ExchangeException.class)
                .hasMessageContaining("did not return an email");

        verify(refreshTokenRepository, never()).save(any());
    }

    // ==================== Helper Methods ====================

    @SuppressWarnings("unchecked")
    private void setupTokenExchangeSuccess() {
        when(restClient.post()).thenReturn(requestBodyUriSpec);
        when(requestBodyUriSpec.uri(anyString())).thenReturn(requestBodySpec);
        when(requestBodySpec.header(anyString(), anyString())).thenReturn(requestBodySpec);
        when(requestBodySpec.body(any())).thenReturn(requestBodySpec);
        when(requestBodySpec.retrieve()).thenReturn(responseSpec);
        when(responseSpec.body(eq(Map.class)))
                .thenReturn(Map.of("access_token", "provider-access-token-xyz"));
    }

    @SuppressWarnings("unchecked")
    private void setupTokenExchangeFailure(RestClientException exception) {
        when(restClient.post()).thenReturn(requestBodyUriSpec);
        when(requestBodyUriSpec.uri(anyString())).thenReturn(requestBodySpec);
        when(requestBodySpec.header(anyString(), anyString())).thenReturn(requestBodySpec);
        when(requestBodySpec.body(any())).thenReturn(requestBodySpec);
        when(requestBodySpec.retrieve()).thenThrow(exception);
    }

    @SuppressWarnings("unchecked")
    private void setupTokenExchangeDenied() {
        when(restClient.post()).thenReturn(requestBodyUriSpec);
        when(requestBodyUriSpec.uri(anyString())).thenReturn(requestBodySpec);
        when(requestBodySpec.header(anyString(), anyString())).thenReturn(requestBodySpec);
        when(requestBodySpec.body(any())).thenReturn(requestBodySpec);
        when(requestBodySpec.retrieve()).thenReturn(responseSpec);
        when(responseSpec.body(eq(Map.class)))
                .thenReturn(Map.of("error", "access_denied", "error_description", "User denied access"));
    }

    @SuppressWarnings("unchecked")
    private void setupUserInfoSuccess(String email, String subject) {
        setupUserInfoResponseMap(Map.of("email", email, "sub", subject));
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private void setupUserInfoResponseMap(Map<String, Object> responseMap) {
        RestClient.RequestHeadersUriSpec getSpec = org.mockito.Mockito.mock(RestClient.RequestHeadersUriSpec.class);
        RestClient.RequestHeadersSpec headersSpec = org.mockito.Mockito.mock(RestClient.RequestHeadersSpec.class);
        RestClient.ResponseSpec getResponseSpec = org.mockito.Mockito.mock(RestClient.ResponseSpec.class);

        when(restClient.get()).thenReturn(getSpec);
        when(getSpec.uri(anyString())).thenReturn(headersSpec);
        when(headersSpec.header(anyString(), anyString())).thenReturn(headersSpec);
        when(headersSpec.retrieve()).thenReturn(getResponseSpec);
        when(getResponseSpec.body(eq(Map.class))).thenReturn(responseMap);
    }

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
