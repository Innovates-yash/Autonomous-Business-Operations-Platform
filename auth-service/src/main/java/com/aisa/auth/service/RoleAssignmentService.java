package com.aisa.auth.service;

import com.aisa.auth.domain.RefreshToken;
import com.aisa.auth.domain.Role;
import com.aisa.auth.domain.RoleName;
import com.aisa.auth.domain.User;
import com.aisa.auth.repository.RefreshTokenRepository;
import com.aisa.auth.repository.RoleRepository;
import com.aisa.auth.repository.UserRepository;
import java.util.List;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Admin-only role assignment service (Requirements 2.1, 2.13, 2.14).
 *
 * <p>Changes a user's role subject to the following constraints:
 * <ul>
 *   <li>Only an Admin may change roles (Requirement 2.7).</li>
 *   <li>Each user has exactly one role (Requirement 2.1).</li>
 *   <li>On role change, all active refresh tokens for the target user are revoked
 *       so their next authentication picks up the new role within 5 seconds
 *       (Requirement 2.13, 2.14).</li>
 *   <li>On role change, the Redis-cached authorization decisions for the target user
 *       are evicted so subsequent requests see the new permissions immediately
 *       (Requirement 2.14).</li>
 * </ul>
 */
@Service
public class RoleAssignmentService {

    private final UserRepository userRepository;
    private final RoleRepository roleRepository;
    private final RefreshTokenRepository refreshTokenRepository;

    public RoleAssignmentService(
            UserRepository userRepository,
            RoleRepository roleRepository,
            RefreshTokenRepository refreshTokenRepository) {
        this.userRepository = userRepository;
        this.roleRepository = roleRepository;
        this.refreshTokenRepository = refreshTokenRepository;
    }

    /**
     * Assigns a new role to the target user. Only Admins may invoke this operation.
     *
     * <p>On successful role change, the {@code rolePermissions} cache is evicted for the
     * target user (all entries matching the user id prefix) so the new role's permissions
     * take effect immediately on the next authorization decision (Req 2.14).
     *
     * @param callerRole the role of the user performing the assignment
     * @param targetUserId the id of the user whose role is being changed
     * @param newRoleName the new role to assign
     * @return the updated user
     * @throws AdminOnlyOperationException if the caller is not an Admin
     * @throws UserNotFoundException if the target user does not exist
     * @throws RoleNotFoundException if the requested role does not exist
     */
    @Transactional
    @CacheEvict(value = "rolePermissions", allEntries = true)
    public User assignRole(RoleName callerRole, Long targetUserId, RoleName newRoleName) {
        // Requirement 2.7: only Admin may assign roles.
        if (callerRole != RoleName.ADMIN) {
            throw new AdminOnlyOperationException("Only Admin users may assign roles");
        }

        User targetUser = userRepository.findById(targetUserId)
                .orElseThrow(() -> new UserNotFoundException(
                        "User not found: " + targetUserId));

        Role newRole = roleRepository.findByName(newRoleName)
                .orElseThrow(() -> new RoleNotFoundException(
                        "Role not found: " + newRoleName));

        // If the role is unchanged, return early without token revocation.
        if (targetUser.getRole().getName() == newRoleName) {
            return targetUser;
        }

        // Requirement 2.14: invalidate permissions cached under the user's previous role.
        // Revoke all active refresh tokens so re-authentication picks up the new role
        // within 5 seconds (Requirement 2.13).
        revokeActiveRefreshTokens(targetUser);

        // Apply the new role (single role per user, Requirement 2.1).
        targetUser.setRole(newRole);
        return userRepository.save(targetUser);
    }

    /**
     * Revokes all active (non-revoked) refresh tokens for the target user.
     * This forces re-authentication, ensuring the new role's permissions take
     * effect within 5 seconds (Requirements 2.13, 2.14).
     */
    private void revokeActiveRefreshTokens(User user) {
        List<RefreshToken> activeTokens = refreshTokenRepository.findByUserAndRevokedFalse(user);
        for (RefreshToken token : activeTokens) {
            token.setRevoked(true);
        }
        if (!activeTokens.isEmpty()) {
            refreshTokenRepository.saveAll(activeTokens);
        }
    }
}
