package com.aisa.auth.web;

import com.aisa.auth.service.AuthenticationService;
import com.aisa.auth.service.RegistrationService;
import com.aisa.auth.web.dto.LoginRequest;
import com.aisa.auth.web.dto.LogoutRequest;
import com.aisa.auth.web.dto.RefreshRequest;
import com.aisa.auth.web.dto.RegistrationRequest;
import com.aisa.auth.web.dto.RegistrationResponse;
import com.aisa.auth.web.dto.TokenResponse;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Authentication HTTP API: registration (Requirements 1.1, 1.2, 1.12), login and
 * token issuance (Requirements 1.3, 1.4, 1.5, 1.9), refresh rotation (Requirements
 * 1.6, 1.7), and sign-out invalidation (Requirement 1.10). Account lockout
 * (Requirements 1.11, 1.14) is delivered by a separate task.
 */
@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final RegistrationService registrationService;
    private final AuthenticationService authenticationService;

    public AuthController(
            RegistrationService registrationService,
            AuthenticationService authenticationService) {
        this.registrationService = registrationService;
        this.authenticationService = authenticationService;
    }

    /**
     * Registers a new account. Returns {@code 201 Created} with the confirmation
     * result on success (Requirement 1.1). Validation failures (Requirement 1.12)
     * and duplicate emails (Requirement 1.2) are translated to client-safe error
     * payloads by {@link AuthExceptionHandler}.
     */
    @PostMapping("/register")
    public ResponseEntity<RegistrationResponse> register(
            @Valid @RequestBody RegistrationRequest request) {
        RegistrationResponse response = registrationService.register(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    /**
     * Verifies credentials and issues a JWT access token plus a refresh token
     * (Requirements 1.3, 1.4, 1.5). Invalid credentials yield a uniform authentication
     * error that does not reveal which field was wrong (Requirement 1.9), translated by
     * {@link AuthExceptionHandler}.
     */
    @PostMapping("/login")
    public ResponseEntity<TokenResponse> login(@Valid @RequestBody LoginRequest request) {
        TokenResponse response = authenticationService.login(request.email(), request.password());
        return ResponseEntity.ok(response);
    }

    /**
     * Rotates a valid refresh token into a fresh access/refresh pair (Requirement 1.6).
     * Expired, used, or revoked tokens are rejected with an authentication error
     * (Requirement 1.7).
     */
    @PostMapping("/refresh")
    public ResponseEntity<TokenResponse> refresh(@Valid @RequestBody RefreshRequest request) {
        TokenResponse response = authenticationService.refresh(request.refreshToken());
        return ResponseEntity.ok(response);
    }

    /**
     * Invalidates the user's refresh token(s) on sign-out (Requirement 1.10). Idempotent:
     * returns {@code 204 No Content} whether or not the token was still active.
     */
    @PostMapping("/logout")
    public ResponseEntity<Void> logout(@Valid @RequestBody LogoutRequest request) {
        authenticationService.logout(request.refreshToken());
        return ResponseEntity.noContent().build();
    }
}
