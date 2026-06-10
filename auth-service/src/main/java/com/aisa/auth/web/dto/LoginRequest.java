package com.aisa.auth.web.dto;

import jakarta.validation.constraints.NotBlank;

/**
 * Login payload (Requirement 1.3). Only presence is validated at the boundary;
 * correctness is decided by credential verification, which returns a uniform error
 * on any failure so neither field is singled out (Requirement 1.9).
 *
 * @param email    the account email address
 * @param password the plaintext password (never persisted)
 */
public record LoginRequest(
        @NotBlank(message = "Email is required") String email,
        @NotBlank(message = "Password is required") String password) {
}
