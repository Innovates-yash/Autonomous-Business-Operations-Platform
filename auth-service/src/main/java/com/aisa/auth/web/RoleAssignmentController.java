package com.aisa.auth.web;

import com.aisa.auth.domain.RoleName;
import com.aisa.auth.domain.User;
import com.aisa.auth.service.RoleAssignmentService;
import com.aisa.auth.web.dto.RoleAssignRequest;
import com.aisa.auth.web.dto.RoleAssignmentResponse;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Role assignment HTTP API at the {@code /api/auth/roles} path
 * (Requirements 2.1, 2.7, 2.13, 2.14).
 *
 * <p>Accepts a userId and targetRole in the body. The caller's role is extracted
 * from the {@code X-User-Role} header, populated by the API Gateway after JWT
 * validation. Only Admin callers may assign roles.
 *
 * <p>On success, the target user's active refresh tokens are revoked so the
 * new role's permissions take effect within 5 seconds (Requirements 2.13, 2.14).
 */
@RestController
@RequestMapping("/api/auth/roles")
public class RoleAssignmentController {

    private final RoleAssignmentService roleAssignmentService;

    public RoleAssignmentController(RoleAssignmentService roleAssignmentService) {
        this.roleAssignmentService = roleAssignmentService;
    }

    /**
     * Assigns a role to a user. Admin-only (Requirement 2.7).
     *
     * @param callerRole the role of the calling user (injected by gateway from JWT claims)
     * @param request must contain userId and targetRole
     * @return the updated user with their new role
     */
    @PostMapping("/assign")
    public ResponseEntity<RoleAssignmentResponse> assignRole(
            @RequestHeader(value = "X-User-Role") String callerRole,
            @Valid @RequestBody RoleAssignRequest request) {

        RoleName callerRoleName = parseRoleName(callerRole);
        RoleName newRoleName = parseRoleName(request.targetRole());

        User updated = roleAssignmentService.assignRole(callerRoleName, request.userId(), newRoleName);
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
