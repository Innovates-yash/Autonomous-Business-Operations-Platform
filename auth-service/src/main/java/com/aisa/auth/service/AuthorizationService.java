package com.aisa.auth.service;

import com.aisa.auth.domain.Permission;
import com.aisa.auth.domain.Role;
import com.aisa.auth.domain.User;
import com.aisa.auth.repository.UserRepository;
import com.aisa.auth.web.dto.AuthorizeResponse;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Authorization decision logic (Requirements 2.3–2.6).
 *
 * <p>Evaluates whether a user's assigned role holds the required permission and
 * returns a PERMIT or DENY decision. On DENY, no state change is performed
 * (Requirement 2.5). If the user has no role assigned, the decision is DENY
 * (Requirement 2.6).
 *
 * <p>The decision must be returned within 500ms (Requirement 2.4). Role/permission
 * lookups are cached in Redis (cache name: {@code rolePermissions}) so repeated
 * authorization checks for the same user and permission resolve from shared cache
 * rather than hitting the database. This supports stateless horizontal scaling
 * (Req 26.2) and keeps decisions within the 500 ms budget even under high load.
 *
 * <p>Cache invalidation occurs via {@link RoleAssignmentService} on role change
 * (Req 2.13, 2.14).
 */
@Service
public class AuthorizationService {

    private final UserRepository userRepository;

    public AuthorizationService(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    /**
     * Returns a PERMIT or DENY decision for the given user and permission.
     *
     * <p>Results are cached in Redis under the {@code rolePermissions} cache with key
     * {@code userId:permission}. Cache TTL is 30 seconds to balance freshness with
     * performance. On role change, the cache is evicted by {@link RoleAssignmentService}.
     *
     * <p>This method is read-only: on DENY no writes or side effects occur
     * (Requirement 2.5).
     *
     * @param userId     the user requesting the action
     * @param permission the permission required for the action
     * @return PERMIT if the user's role holds the permission, DENY otherwise
     */
    @Transactional(readOnly = true)
    @Cacheable(value = "rolePermissions", key = "#userId + ':' + #permission")
    public AuthorizeResponse decide(Long userId, String permission) {
        // Load the user with their role (eager fetch configured in User entity)
        User user = userRepository.findById(userId).orElse(null);

        // Requirement 2.6: no user found → DENY
        if (user == null) {
            return AuthorizeResponse.deny();
        }

        Role role = user.getRole();

        // Requirement 2.6: no role assigned → DENY
        if (role == null) {
            return AuthorizeResponse.deny();
        }

        // Requirement 2.3: permit only if the role holds the required permission
        boolean hasPermission = role.getPermissions().stream()
                .map(Permission::getName)
                .anyMatch(p -> p.equals(permission));

        if (hasPermission) {
            return AuthorizeResponse.permit();
        }

        // Requirement 2.5: on deny, no state change — this is a read-only path
        return AuthorizeResponse.deny();
    }
}
