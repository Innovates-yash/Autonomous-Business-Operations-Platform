package com.aisa.auth.web;

import com.aisa.auth.service.AuthorizationService;
import com.aisa.auth.web.dto.AuthorizeRequest;
import com.aisa.auth.web.dto.AuthorizeResponse;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Authorization decision endpoint (Requirements 2.3–2.6).
 *
 * <p>Accepts a user identifier and a permission name, evaluates whether the
 * user's role holds the permission, and returns a PERMIT or DENY decision
 * within 500ms (Requirement 2.4). On DENY no state is mutated (Requirement 2.5).
 */
@RestController
@RequestMapping("/api/auth")
public class AuthorizationController {

    private final AuthorizationService authorizationService;

    public AuthorizationController(AuthorizationService authorizationService) {
        this.authorizationService = authorizationService;
    }

    /**
     * POST /api/auth/authorize
     *
     * <p>Returns {@code {"decision": "PERMIT"}} or {@code {"decision": "DENY"}}
     * based on the user's role and the required permission.
     */
    @PostMapping("/authorize")
    public ResponseEntity<AuthorizeResponse> authorize(
            @Valid @RequestBody AuthorizeRequest request) {
        AuthorizeResponse response = authorizationService.decide(
                request.userId(), request.permission());
        return ResponseEntity.ok(response);
    }
}
