package com.aisa.auth.service;

import com.aisa.auth.config.AuthTokenProperties;
import com.aisa.auth.domain.LoginAttempt;
import com.aisa.auth.domain.RefreshToken;
import com.aisa.auth.domain.User;
import com.aisa.auth.repository.LoginAttemptRepository;
import com.aisa.auth.repository.RefreshTokenRepository;
import com.aisa.auth.repository.UserRepository;
import com.aisa.auth.web.dto.TokenResponse;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.List;
import java.util.Locale;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Authenticates users and manages the access/refresh token lifecycle
 * (Requirements 1.3, 1.5, 1.6, 1.7, 1.9, 1.10, 1.11, 1.14).
 *
 * <p>Login verifies the BCrypt password hash and, on success, issues a signed JWT
 * access token together with an opaque refresh token whose SHA-256 hash is stored.
 * A failed login always raises the same uniform error so the response cannot reveal
 * whether the email or the password was at fault (Requirement 1.9).
 *
 * <p>Refresh enforces single-use rotation: the presented token is validated by hash,
 * marked used, and linked to the replacement it issues; expired, used, or revoked
 * tokens are rejected (Requirements 1.6, 1.7). Logout revokes the user's active
 * refresh tokens (Requirement 1.10).
 *
 * <p>Account lockout (Requirements 1.11, 1.14): after 5 failed attempts in a rolling
 * 15-minute window the account is locked for 15 minutes. During lockout, login
 * attempts are rejected immediately without evaluating credentials.
 */
@Service
public class AuthenticationService {

    /** Refresh token entropy: 256 bits of secure randomness, URL-safe base64 encoded. */
    private static final int REFRESH_TOKEN_BYTES = 32;

    /** Maximum failed login attempts before lockout (Requirement 1.11). */
    static final int MAX_FAILED_ATTEMPTS = 5;

    /** Rolling window and lockout duration (Requirement 1.11). */
    static final Duration LOCKOUT_WINDOW = Duration.ofMinutes(15);

    private final UserRepository userRepository;
    private final RefreshTokenRepository refreshTokenRepository;
    private final LoginAttemptRepository loginAttemptRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final AuthTokenProperties tokenProperties;
    private final SecureRandom secureRandom = new SecureRandom();

    public AuthenticationService(
            UserRepository userRepository,
            RefreshTokenRepository refreshTokenRepository,
            LoginAttemptRepository loginAttemptRepository,
            PasswordEncoder passwordEncoder,
            JwtService jwtService,
            AuthTokenProperties tokenProperties) {
        this.userRepository = userRepository;
        this.refreshTokenRepository = refreshTokenRepository;
        this.loginAttemptRepository = loginAttemptRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtService = jwtService;
        this.tokenProperties = tokenProperties;
    }

    /**
     * Verifies credentials and issues a new access/refresh token pair.
     *
     * <p>Before evaluating credentials, checks whether the account is currently locked
     * (Requirement 1.14). If locked and the lock has not expired, rejects immediately
     * with {@link AccountLockedException} without evaluating credentials.
     *
     * <p>On failed login, records the attempt and locks the account if 5 failures
     * occurred within the rolling 15-minute window (Requirement 1.11). On successful
     * login, clears any existing lock state and resets the attempt counter.
     *
     * @param email       the submitted email address
     * @param rawPassword the submitted plaintext password
     * @return the issued tokens
     * @throws AccountLockedException      if the account is locked (Req 1.14)
     * @throws InvalidCredentialsException on any authentication failure (uniform, Req 1.9)
     */
    @Transactional
    public TokenResponse login(String email, String rawPassword) {
        String normalized = normalizeEmail(email);
        Instant now = Instant.now();

        User user = userRepository.findByEmail(normalized).orElse(null);

        // Requirement 1.14: reject locked accounts immediately without credential evaluation.
        if (user != null && isAccountCurrentlyLocked(user, now)) {
            throw new AccountLockedException();
        }

        // Credential evaluation.
        if (user == null
                || user.getPasswordHash() == null
                || !passwordEncoder.matches(rawPassword, user.getPasswordHash())) {
            // Record the failed attempt.
            recordFailedAttempt(normalized, now);

            // Check if lockout threshold is reached (Requirement 1.11).
            if (user != null) {
                Instant windowStart = now.minus(LOCKOUT_WINDOW);
                long failedCount = loginAttemptRepository.countFailedAttemptsSince(normalized, windowStart);
                if (failedCount >= MAX_FAILED_ATTEMPTS) {
                    user.setAccountLocked(true);
                    user.setLockExpiresAt(now.plus(LOCKOUT_WINDOW));
                    userRepository.save(user);
                }
            }

            throw new InvalidCredentialsException();
        }

        // Successful login: clear lockout state and record success.
        clearLockoutState(user);
        recordSuccessfulAttempt(normalized, now);

        return issueTokenPair(user);
    }

    /**
     * Checks whether the account is currently in a locked state. If the lock has
     * expired, clears the lock and returns false.
     */
    private boolean isAccountCurrentlyLocked(User user, Instant now) {
        if (!user.isAccountLocked()) {
            return false;
        }
        if (user.getLockExpiresAt() != null && user.getLockExpiresAt().isBefore(now)) {
            // Lock expired — clear it.
            clearLockoutState(user);
            userRepository.save(user);
            return false;
        }
        return true;
    }

    private void clearLockoutState(User user) {
        user.setAccountLocked(false);
        user.setLockExpiresAt(null);
    }

    private void recordFailedAttempt(String email, Instant now) {
        LoginAttempt attempt = new LoginAttempt(email, false, null, now);
        loginAttemptRepository.save(attempt);
    }

    private void recordSuccessfulAttempt(String email, Instant now) {
        LoginAttempt attempt = new LoginAttempt(email, true, null, now);
        loginAttemptRepository.save(attempt);
    }

    /**
     * Validates a refresh token and rotates it: the old token is consumed (single-use)
     * and a brand-new access/refresh pair is issued (Requirements 1.6, 1.7).
     *
     * @param rawRefreshToken the opaque refresh token presented by the client
     * @return the newly issued tokens
     * @throws InvalidRefreshTokenException if the token is unknown, expired, used, or revoked
     */
    @Transactional
    public TokenResponse refresh(String rawRefreshToken) {
        String tokenHash = sha256Hex(rawRefreshToken);

        RefreshToken existing = refreshTokenRepository.findByTokenHash(tokenHash)
                .orElseThrow(InvalidRefreshTokenException::new);

        Instant now = Instant.now();
        if (existing.isRevoked()
                || existing.isUsed()
                || existing.getExpiresAt().isBefore(now)) {
            throw new InvalidRefreshTokenException();
        }

        User user = existing.getUser();

        // Mint the replacement, then mark the presented token as consumed and link it
        // to its successor so the rotation chain is auditable (single-use, Req 1.6).
        String newRawRefresh = generateRefreshTokenValue();
        String newRefreshHash = sha256Hex(newRawRefresh);

        existing.setUsed(true);
        existing.setReplacedByTokenHash(newRefreshHash);

        persistRefreshToken(user, newRefreshHash, now);
        IssuedAccessToken accessToken = jwtService.issue(user);

        return buildResponse(accessToken, newRawRefresh);
    }

    /**
     * Revokes the user's active refresh tokens to invalidate the session
     * (Requirement 1.10). Idempotent: an unrecognized token is a no-op.
     *
     * @param rawRefreshToken the refresh token identifying the session to end
     */
    @Transactional
    public void logout(String rawRefreshToken) {
        String tokenHash = sha256Hex(rawRefreshToken);
        refreshTokenRepository.findByTokenHash(tokenHash).ifPresent(token -> {
            List<RefreshToken> active = refreshTokenRepository.findByUserAndRevokedFalse(token.getUser());
            for (RefreshToken activeToken : active) {
                activeToken.setRevoked(true);
            }
        });
    }

    private TokenResponse issueTokenPair(User user) {
        Instant now = Instant.now();
        String rawRefresh = generateRefreshTokenValue();
        persistRefreshToken(user, sha256Hex(rawRefresh), now);
        IssuedAccessToken accessToken = jwtService.issue(user);
        return buildResponse(accessToken, rawRefresh);
    }

    private RefreshToken persistRefreshToken(User user, String tokenHash, Instant issuedAt) {
        Instant expiresAt = issuedAt.plus(tokenProperties.getRefreshTokenTtl());
        RefreshToken token = new RefreshToken(user, tokenHash, issuedAt, expiresAt);
        return refreshTokenRepository.save(token);
    }

    private static TokenResponse buildResponse(IssuedAccessToken accessToken, String rawRefresh) {
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

    /** SHA-256 of the raw token, hex-encoded; only this digest is ever stored. */
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
            // SHA-256 is mandated by the JLS to be present on every JVM.
            throw new IllegalStateException("SHA-256 algorithm not available", e);
        }
    }

    private static String normalizeEmail(String email) {
        return email == null ? null : email.trim().toLowerCase(Locale.ROOT);
    }
}
