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
import com.aisa.auth.domain.LoginAttempt;
import com.aisa.auth.domain.RefreshToken;
import com.aisa.auth.domain.Role;
import com.aisa.auth.domain.RoleName;
import com.aisa.auth.domain.User;
import com.aisa.auth.repository.LoginAttemptRepository;
import com.aisa.auth.repository.RefreshTokenRepository;
import com.aisa.auth.repository.UserRepository;
import com.aisa.auth.web.dto.TokenResponse;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import java.lang.reflect.Field;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import javax.crypto.SecretKey;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

/**
 * Unit tests for {@link AuthenticationService}: token issuance on login
 * (Requirements 1.3, 1.4, 1.5), the uniform invalid-credentials error
 * (Requirement 1.9), single-use refresh rotation and rejection of
 * expired/used/revoked/unknown tokens (Requirements 1.6, 1.7), and logout
 * invalidation (Requirement 1.10).
 */
@ExtendWith(MockitoExtension.class)
class AuthenticationServiceTest {

    private static final String SECRET = "test-secret-test-secret-test-secret-1234567890";
    private static final String RAW_PASSWORD = "Abcdefg1!xyz";

    @Mock
    private UserRepository userRepository;

    @Mock
    private RefreshTokenRepository refreshTokenRepository;

    @Mock
    private LoginAttemptRepository loginAttemptRepository;

    private final PasswordEncoder passwordEncoder = new BCryptPasswordEncoder();
    private final SecretKey verifyKey =
            Keys.hmacShaKeyFor(SECRET.getBytes(StandardCharsets.UTF_8));

    private AuthTokenProperties tokenProperties;
    private AuthenticationService service;

    @BeforeEach
    void setUp() {
        tokenProperties = new AuthTokenProperties();
        tokenProperties.setSecret(SECRET);
        tokenProperties.setIssuer("aisa-auth");
        tokenProperties.setAccessTokenTtl(Duration.ofMinutes(15));
        tokenProperties.setRefreshTokenTtl(Duration.ofDays(7));

        JwtService jwtService = new JwtService(tokenProperties);
        service = new AuthenticationService(
                userRepository, refreshTokenRepository, loginAttemptRepository,
                passwordEncoder, jwtService, tokenProperties);
    }

    private User userWithPassword(String rawPassword) {
        Role role = new Role(RoleName.CLIENT);
        User user = new User("user@example.com", passwordEncoder.encode(rawPassword), role);
        setId(user, 42L);
        return user;
    }

    @Test
    void loginIssuesVerifiableAccessTokenAndStoresHashedRefreshToken() {
        User user = userWithPassword(RAW_PASSWORD);
        when(userRepository.findByEmail("user@example.com")).thenReturn(Optional.of(user));
        when(refreshTokenRepository.save(any(RefreshToken.class))).thenAnswer(inv -> inv.getArgument(0));
        when(loginAttemptRepository.save(any(LoginAttempt.class))).thenAnswer(inv -> inv.getArgument(0));

        TokenResponse response = service.login("User@Example.com", RAW_PASSWORD);

        // Access token is a signed JWT (Req 1.3) carrying the subject and role.
        assertThat(response.accessToken()).isNotBlank();
        assertThat(response.refreshToken()).isNotBlank();
        assertThat(response.tokenType()).isEqualTo("Bearer");
        // 15-minute TTL (Req 1.4).
        assertThat(response.expiresInSeconds()).isEqualTo(900L);

        // The JWT verifies with the shared secret (API Gateway compatibility).
        Claims claims = Jwts.parser().verifyWith(verifyKey).build()
                .parseSignedClaims(response.accessToken()).getPayload();
        assertThat(claims.getSubject()).isEqualTo("42");
        assertThat(claims.get("role", String.class)).isEqualTo(RoleName.CLIENT.name());
        // exp is ~15 minutes after iat (Req 1.4).
        long ttlSeconds = (claims.getExpiration().getTime() - claims.getIssuedAt().getTime()) / 1000;
        assertThat(ttlSeconds).isEqualTo(900L);

        // Only the SHA-256 hash is persisted; the raw refresh value never is (Req 1.5).
        ArgumentCaptor<RefreshToken> captor = ArgumentCaptor.forClass(RefreshToken.class);
        verify(refreshTokenRepository).save(captor.capture());
        RefreshToken stored = captor.getValue();
        assertThat(stored.getTokenHash()).isNotEqualTo(response.refreshToken());
        assertThat(stored.getTokenHash()).hasSize(64); // hex-encoded SHA-256
        assertThat(stored.getUser()).isSameAs(user);
        // 7-day refresh lifetime (Req 1.5).
        long refreshTtlSeconds =
                Duration.between(stored.getIssuedAt(), stored.getExpiresAt()).getSeconds();
        assertThat(refreshTtlSeconds).isEqualTo(Duration.ofDays(7).getSeconds());
    }

    @Test
    void loginWithUnknownEmailThrowsUniformError() {
        when(userRepository.findByEmail("ghost@example.com")).thenReturn(Optional.empty());
        when(loginAttemptRepository.save(any(LoginAttempt.class))).thenAnswer(inv -> inv.getArgument(0));

        assertThatThrownBy(() -> service.login("ghost@example.com", RAW_PASSWORD))
                .isInstanceOf(InvalidCredentialsException.class)
                .hasMessage("Invalid email or password");

        verify(refreshTokenRepository, never()).save(any());
    }

    @Test
    void loginWithWrongPasswordThrowsSameUniformError() {
        User user = userWithPassword(RAW_PASSWORD);
        when(userRepository.findByEmail("user@example.com")).thenReturn(Optional.of(user));
        when(loginAttemptRepository.save(any(LoginAttempt.class))).thenAnswer(inv -> inv.getArgument(0));
        when(loginAttemptRepository.countFailedAttemptsSince(eq("user@example.com"), any(Instant.class)))
                .thenReturn(1L);

        assertThatThrownBy(() -> service.login("user@example.com", "WrongPassw0rd!"))
                .isInstanceOf(InvalidCredentialsException.class)
                .hasMessage("Invalid email or password");

        verify(refreshTokenRepository, never()).save(any());
    }

    @Test
    void loginForOAuthOnlyAccountWithoutPasswordHashThrows() {
        Role role = new Role(RoleName.CLIENT);
        User user = new User("oauth@example.com", null, role);
        setId(user, 7L);
        when(userRepository.findByEmail("oauth@example.com")).thenReturn(Optional.of(user));
        when(loginAttemptRepository.save(any(LoginAttempt.class))).thenAnswer(inv -> inv.getArgument(0));
        when(loginAttemptRepository.countFailedAttemptsSince(eq("oauth@example.com"), any(Instant.class)))
                .thenReturn(1L);

        assertThatThrownBy(() -> service.login("oauth@example.com", RAW_PASSWORD))
                .isInstanceOf(InvalidCredentialsException.class);
    }

    @Test
    void refreshRotatesTokenMarkingOldUsedAndLinkingReplacement() {
        User user = userWithPassword(RAW_PASSWORD);
        RefreshToken active = new RefreshToken(
                user, "oldhash", Instant.now().minusSeconds(60),
                Instant.now().plus(Duration.ofDays(6)));
        when(refreshTokenRepository.findByTokenHash(anyString())).thenReturn(Optional.of(active));
        when(refreshTokenRepository.save(any(RefreshToken.class))).thenAnswer(inv -> inv.getArgument(0));

        TokenResponse response = service.refresh("the-old-raw-refresh-token");

        // Old token is consumed (single-use) and linked to its successor (Req 1.6).
        assertThat(active.isUsed()).isTrue();
        assertThat(active.getReplacedByTokenHash()).isNotNull();

        // A new refresh token is persisted and a new access token issued.
        ArgumentCaptor<RefreshToken> captor = ArgumentCaptor.forClass(RefreshToken.class);
        verify(refreshTokenRepository).save(captor.capture());
        RefreshToken replacement = captor.getValue();
        assertThat(replacement.getTokenHash()).isEqualTo(active.getReplacedByTokenHash());
        assertThat(response.accessToken()).isNotBlank();
        assertThat(response.refreshToken()).isNotBlank();
    }

    @Test
    void refreshRejectsExpiredToken() {
        User user = userWithPassword(RAW_PASSWORD);
        RefreshToken expired = new RefreshToken(
                user, "h", Instant.now().minus(Duration.ofDays(8)),
                Instant.now().minusSeconds(1));
        when(refreshTokenRepository.findByTokenHash(anyString())).thenReturn(Optional.of(expired));

        assertThatThrownBy(() -> service.refresh("raw"))
                .isInstanceOf(InvalidRefreshTokenException.class);
        verify(refreshTokenRepository, never()).save(any());
    }

    @Test
    void refreshRejectsAlreadyUsedToken() {
        User user = userWithPassword(RAW_PASSWORD);
        RefreshToken used = new RefreshToken(
                user, "h", Instant.now().minusSeconds(60), Instant.now().plusSeconds(600));
        used.setUsed(true);
        when(refreshTokenRepository.findByTokenHash(anyString())).thenReturn(Optional.of(used));

        assertThatThrownBy(() -> service.refresh("raw"))
                .isInstanceOf(InvalidRefreshTokenException.class);
        verify(refreshTokenRepository, never()).save(any());
    }

    @Test
    void refreshRejectsRevokedToken() {
        User user = userWithPassword(RAW_PASSWORD);
        RefreshToken revoked = new RefreshToken(
                user, "h", Instant.now().minusSeconds(60), Instant.now().plusSeconds(600));
        revoked.setRevoked(true);
        when(refreshTokenRepository.findByTokenHash(anyString())).thenReturn(Optional.of(revoked));

        assertThatThrownBy(() -> service.refresh("raw"))
                .isInstanceOf(InvalidRefreshTokenException.class);
        verify(refreshTokenRepository, never()).save(any());
    }

    @Test
    void refreshRejectsUnknownToken() {
        when(refreshTokenRepository.findByTokenHash(anyString())).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.refresh("raw"))
                .isInstanceOf(InvalidRefreshTokenException.class);
    }

    @Test
    void logoutRevokesUsersActiveTokens() {
        User user = userWithPassword(RAW_PASSWORD);
        RefreshToken presented = new RefreshToken(
                user, "h", Instant.now().minusSeconds(60), Instant.now().plusSeconds(600));
        RefreshToken otherActive = new RefreshToken(
                user, "h2", Instant.now().minusSeconds(60), Instant.now().plusSeconds(600));
        when(refreshTokenRepository.findByTokenHash(anyString())).thenReturn(Optional.of(presented));
        when(refreshTokenRepository.findByUserAndRevokedFalse(user))
                .thenReturn(List.of(presented, otherActive));

        service.logout("raw");

        assertThat(presented.isRevoked()).isTrue();
        assertThat(otherActive.isRevoked()).isTrue();
    }

    @Test
    void logoutWithUnknownTokenIsNoOp() {
        when(refreshTokenRepository.findByTokenHash(anyString())).thenReturn(Optional.empty());

        service.logout("raw");

        verify(refreshTokenRepository, never()).findByUserAndRevokedFalse(any());
    }

    /** Sets the JPA-generated id on a transient entity for assertion purposes. */
    private static void setId(User user, long id) {
        try {
            Field field = User.class.getDeclaredField("id");
            field.setAccessible(true);
            field.set(user, id);
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException(e);
        }
    }

    // ==================== Account Lockout Tests (Requirements 1.11, 1.14) ====================

    @Test
    void loginLocksAccountAfterFiveFailedAttemptsInWindow() {
        User user = userWithPassword(RAW_PASSWORD);
        when(userRepository.findByEmail("user@example.com")).thenReturn(Optional.of(user));
        when(loginAttemptRepository.save(any(LoginAttempt.class))).thenAnswer(inv -> inv.getArgument(0));
        // After recording the 5th failure, the count returns 5.
        when(loginAttemptRepository.countFailedAttemptsSince(eq("user@example.com"), any(Instant.class)))
                .thenReturn(5L);
        when(userRepository.save(any(User.class))).thenAnswer(inv -> inv.getArgument(0));

        assertThatThrownBy(() -> service.login("user@example.com", "WrongPassw0rd!"))
                .isInstanceOf(InvalidCredentialsException.class);

        // Account should now be locked.
        assertThat(user.isAccountLocked()).isTrue();
        assertThat(user.getLockExpiresAt()).isNotNull();
        verify(userRepository).save(user);
    }

    @Test
    void loginRejectsDuringLockoutWithoutEvaluatingCredentials() {
        User user = userWithPassword(RAW_PASSWORD);
        // Set the account as locked with a future expiration.
        user.setAccountLocked(true);
        user.setLockExpiresAt(Instant.now().plusSeconds(600));
        when(userRepository.findByEmail("user@example.com")).thenReturn(Optional.of(user));

        // Even with the CORRECT password, should be rejected as AccountLockedException.
        assertThatThrownBy(() -> service.login("user@example.com", RAW_PASSWORD))
                .isInstanceOf(AccountLockedException.class);

        // BCrypt compare should never be called — we verify no refresh token was issued
        // and no login attempt was recorded.
        verify(refreshTokenRepository, never()).save(any());
        verify(loginAttemptRepository, never()).save(any());
    }

    @Test
    void loginSucceedsAfterLockExpires() {
        User user = userWithPassword(RAW_PASSWORD);
        // Lock expired 1 second ago.
        user.setAccountLocked(true);
        user.setLockExpiresAt(Instant.now().minusSeconds(1));
        when(userRepository.findByEmail("user@example.com")).thenReturn(Optional.of(user));
        when(userRepository.save(any(User.class))).thenAnswer(inv -> inv.getArgument(0));
        when(refreshTokenRepository.save(any(RefreshToken.class))).thenAnswer(inv -> inv.getArgument(0));
        when(loginAttemptRepository.save(any(LoginAttempt.class))).thenAnswer(inv -> inv.getArgument(0));

        TokenResponse response = service.login("user@example.com", RAW_PASSWORD);

        assertThat(response.accessToken()).isNotBlank();
        // Lock state should have been cleared.
        assertThat(user.isAccountLocked()).isFalse();
        assertThat(user.getLockExpiresAt()).isNull();
    }

    @Test
    void successfulLoginClearsLockState() {
        User user = userWithPassword(RAW_PASSWORD);
        // User previously had a lock that expired, but the flag wasn't cleared yet by another path.
        user.setAccountLocked(true);
        user.setLockExpiresAt(Instant.now().minusSeconds(60));
        when(userRepository.findByEmail("user@example.com")).thenReturn(Optional.of(user));
        when(userRepository.save(any(User.class))).thenAnswer(inv -> inv.getArgument(0));
        when(refreshTokenRepository.save(any(RefreshToken.class))).thenAnswer(inv -> inv.getArgument(0));
        when(loginAttemptRepository.save(any(LoginAttempt.class))).thenAnswer(inv -> inv.getArgument(0));

        TokenResponse response = service.login("user@example.com", RAW_PASSWORD);

        assertThat(response.accessToken()).isNotBlank();
        assertThat(user.isAccountLocked()).isFalse();
        assertThat(user.getLockExpiresAt()).isNull();
    }

    @Test
    void failedLoginRecordsAttemptButDoesNotLockWhenBelowThreshold() {
        User user = userWithPassword(RAW_PASSWORD);
        when(userRepository.findByEmail("user@example.com")).thenReturn(Optional.of(user));
        when(loginAttemptRepository.save(any(LoginAttempt.class))).thenAnswer(inv -> inv.getArgument(0));
        // Only 3 failures so far — below the threshold of 5.
        when(loginAttemptRepository.countFailedAttemptsSince(eq("user@example.com"), any(Instant.class)))
                .thenReturn(3L);

        assertThatThrownBy(() -> service.login("user@example.com", "WrongPassw0rd!"))
                .isInstanceOf(InvalidCredentialsException.class);

        // Attempt recorded but account NOT locked.
        verify(loginAttemptRepository).save(any(LoginAttempt.class));
        assertThat(user.isAccountLocked()).isFalse();
    }
}
