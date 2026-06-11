package com.aisa.auth.web;

import com.aisa.auth.domain.RoleName;
import com.aisa.auth.domain.User;
import com.aisa.auth.service.RoleAssignmentService;
import com.aisa.auth.web.dto.RoleAssignmentRequest;
import com.aisa.auth.web.dto.RoleAssignmentResponse;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Admin HTTP API for user management operations (Requirements 2.7, 2.13, 2.14).
 *
 * <p>The caller's role is extracted from the {@code X-User-Role} header, which is
 * populated by the API Gateway after JWT validation. In production, the gateway
 * strips any client-supplied value and replaces it with the claim from the verified
 * token, so clients cannot forge the header.
 */
@RestController
@RequestMapping("/api/admin")
public class AdminController {

    private final RoleAssignmentService roleAssignmentService;

    public AdminController(RoleAssignmentService roleAssignmentService) {
        this.roleAssignmentService = roleAssignmentService;
    }

    /**
     * Changes the role assigned to a user. Admin-only (Requirement 2.7).
     *
     * <p>On success, the target user's active refresh tokens are revoked so the
     * new role's permissions take effect within 5 seconds (Requirements 2.13, 2.14).
     *
     * @param userId the id of the user whose role is being changed
     * @param callerRole the role of the calling user (injected by gateway from JWT claims)
     * @param request the new role to assign
     * @return the updated user with their new role
     */
    @PostMapping("/users/{userId}/role")
    public ResponseEntity<RoleAssignmentResponse> assignRole(
            @PathVariable Long userId,
            @RequestHeader(value = "X-User-Role") String callerRole,
            @Valid @RequestBody RoleAssignmentRequest request) {

        RoleName callerRoleName = parseRoleName(callerRole);
        RoleName newRoleName = parseRoleName(request.role());

        User updated = roleAssignmentService.assignRole(callerRoleName, userId, newRoleName);
        return ResponseEntity.ok(RoleAssignmentResponse.from(updated));
    }

    private static RoleName parseRoleName(String roleName) {
        try {
            return RoleName.valueOf(roleName.trim().toUpperCase());
        } catch (IllegalArgumentException | NullPointerException e) {
            throw new InvalidRoleNameException("Invalid role name: " + roleName);
        }
    }
}
