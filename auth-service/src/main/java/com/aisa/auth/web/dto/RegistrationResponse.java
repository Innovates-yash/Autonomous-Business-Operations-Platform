package com.aisa.auth.web.dto;

import com.aisa.auth.domain.User;
import java.time.Instant;

/**
 * Confirmation result returned when a User account is created (Requirement 1.1).
 * Carries only non-sensitive account facts; the password hash is never exposed.
 *
 * @param id        the new account identifier
 * @param email     the stored (normalized) email address
 * @param role      the assigned role name (GUEST by default, Requirement 2.2)
 * @param createdAt account creation timestamp
 */
public record RegistrationResponse(Long id, String email, String role, Instant createdAt) {

    public static RegistrationResponse from(User user) {
        return new RegistrationResponse(
                user.getId(),
                user.getEmail(),
                user.getRole().getName().name(),
                user.getCreatedAt());
    }
}
