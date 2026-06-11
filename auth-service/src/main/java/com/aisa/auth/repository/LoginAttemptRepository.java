package com.aisa.auth.repository;

import com.aisa.auth.domain.LoginAttempt;
import java.time.Instant;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

/**
 * Spring Data JPA repository for {@link LoginAttempt} records.
 *
 * <p>Supports account lockout (Requirement 1.11) by counting failed login attempts
 * within a rolling 15-minute window keyed by email address.
 */
@Repository
public interface LoginAttemptRepository extends JpaRepository<LoginAttempt, Long> {

    /**
     * Counts the number of failed login attempts for the given email since the
     * specified cutoff time. Used to determine whether the lockout threshold (5)
     * has been reached within the rolling 15-minute window (Requirement 1.11).
     *
     * @param email  the normalized email address
     * @param since  the start of the rolling window (now minus 15 minutes)
     * @return the count of failed attempts in the window
     */
    @Query("SELECT COUNT(la) FROM LoginAttempt la "
            + "WHERE la.email = :email AND la.successful = false AND la.attemptedAt >= :since")
    long countFailedAttemptsSince(@Param("email") String email, @Param("since") Instant since);
}
