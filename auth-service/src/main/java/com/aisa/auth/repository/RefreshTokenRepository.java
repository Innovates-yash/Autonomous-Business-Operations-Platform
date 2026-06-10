package com.aisa.auth.repository;

import com.aisa.auth.domain.RefreshToken;
import com.aisa.auth.domain.User;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

/**
 * Spring Data JPA repository for {@link RefreshToken} entities.
 *
 * <p>Refresh and rotation locate a token by its stored SHA-256 hash
 * ({@link #findByTokenHash(String)}) — the raw value is never persisted
 * (Requirements 1.6, 1.7). Logout revokes every still-active token for the user
 * ({@link #findByUserAndRevokedFalse(User)}) to invalidate the session
 * (Requirement 1.10).
 */
@Repository
public interface RefreshTokenRepository extends JpaRepository<RefreshToken, Long> {

    Optional<RefreshToken> findByTokenHash(String tokenHash);

    List<RefreshToken> findByUserAndRevokedFalse(User user);
}
