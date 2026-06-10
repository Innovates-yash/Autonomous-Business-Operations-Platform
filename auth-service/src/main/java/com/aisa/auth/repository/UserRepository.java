package com.aisa.auth.repository;

import com.aisa.auth.domain.User;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

/**
 * Spring Data JPA repository for {@link User} aggregates.
 *
 * <p>Registration relies on {@link #existsByEmail(String)} to reject duplicate
 * accounts (Requirement 1.2) and on {@link #findByEmail(String)} for later
 * credential verification (Requirement 1.3). Emails are stored normalized
 * (trimmed, lower-cased) so uniqueness checks are case-insensitive.
 */
@Repository
public interface UserRepository extends JpaRepository<User, Long> {

    boolean existsByEmail(String email);

    Optional<User> findByEmail(String email);
}
