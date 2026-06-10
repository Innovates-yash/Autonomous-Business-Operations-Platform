package com.aisa.auth.service;

import com.aisa.auth.config.AuthTokenProperties;
import com.aisa.auth.domain.RefreshToken;
import com.aisa.auth.domain.User;
import com.aisa.auth.repository.RefreshTokenRepository;
import com.aisa.auth.repository.UserRepository;
import com.aisa.auth.web.dto.TokenResponse;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Instant;
import java.util.Base64;
import java.util.List;
import java.util.Locale;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Authenticates users and manages the access/refresh token lifecycle
 * (Requirements 1.3, 1.5, 1.6, 1.7, 1.9, 1.10).
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
 * <p>Account lockout (Requirements 1.11, 1.14) is intentionally out of scope here and
 * delivered by a separate task.
 */
@Service
public class AuthenticationService {

    /** Refresh token entropy: 256 bits of secure randomness, URL-safe base64 encoded. */
    private static final int REFRESH_TOKEN_BYTES = 32;

    private final UserRepository userRepository;
    private final RefreshTokenRepository refreshTokenRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final AuthTokenProperties tokenProperties;
    private final SecureRandom secureRandom = new SecureRandom();

    public AuthenticationService(
            UserRepository userRepository,
            RefreshTokenRepository refreshTokenRepository,
            PasswordEncoder passwordEncoder,
            JwtService jwtService,
            AuthTokenProperties tokenProperties) {
        this.userRepository = userRepository;
        this.refreshTokenRepository = refreshTokenRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtService = jwtService;
        this.tokenProperties = tokenProperties;
    }

    /**
     * Verifies credentials and issues a new access/refresh token pair.
     *
     * @param email       the submitted email address
     * @param rawPassword the submitted plaintext password
     * @return the issued tokens
     * @throws InvalidCredentialsException on any authentication failure (uniform, Req 1.9)
     */
    @Transactional
    public TokenResponse login(String email, String rawPassword) {
        String normalized = normalizeEmail(email);

        User user = userRepository.findByEmail(normalized).orElse(null);
        // Verify a hash even when the user is unknown is unnecessary here, but the
        // response/throw path is identical so the outcome is indistinguishable (Req 1.9).
        if (user == null
                || user.getPasswordHash() == null
                || !passwordEncoder.matches(rawPassword, user.getPasswordHash())) {
            throw new InvalidCredentialsException();
        }

        return issueTokenPair(user);
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
