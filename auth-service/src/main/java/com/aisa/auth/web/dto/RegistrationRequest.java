package com.aisa.auth.web.dto;

import com.aisa.auth.validation.PasswordComplexity;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Registration payload (Requirement 1.1). Field-level constraints enforce the
 * email and password rules and produce per-field validation errors when a
 * requirement is not met (Requirement 1.12):
 *
 * <ul>
 *   <li>email: present, syntactically valid, 1–254 characters</li>
 *   <li>password: present, 12–128 characters, mixed character classes</li>
 * </ul>
 *
 * @param email    the prospective account's email address
 * @param password the plaintext password (never persisted; only its hash is stored)
 */
public record RegistrationRequest(
        @NotBlank(message = "Email is required")
        @Size(min = 1, max = 254, message = "Email must be between 1 and 254 characters")
        @Email(message = "Email must be a valid email address")
        String email,

        @NotBlank(message = "Password is required")
        @Size(min = 12, max = 128, message = "Password must be between 12 and 128 characters")
        @PasswordComplexity
        String password) {
}
