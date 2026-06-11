package com.aisa.auth.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.aisa.auth.domain.RefreshToken;
import com.aisa.auth.domain.Role;
import com.aisa.auth.domain.RoleName;
import com.aisa.auth.domain.User;
import com.aisa.auth.repository.RefreshTokenRepository;
import com.aisa.auth.repository.RoleRepository;
import com.aisa.auth.repository.UserRepository;
import java.time.Instant;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * Unit tests for {@link RoleAssignmentService} verifying:
 * - Single role per user enforced (Requirement 2.1)
 * - Guest default on registration (Requirement 2.2) — via RegistrationService
 * - Admin-only assignment (Requirement 2.7)
 * - Non-Admin rejected (Requirement 2.7)
 * - Refresh tokens revoked on role change (Requirements 2.13, 2.14)
 */
@ExtendWith(MockitoExtension.class)
class RoleAssignmentServiceTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private RoleRepository roleRepository;

    @Mock
    private RefreshTokenRepository refreshTokenRepository;

    private RoleAssignmentService service;

    @BeforeEach
    void setUp() {
        service = new RoleAssignmentService(userRepository, roleRepository, refreshTokenRepository);
    }

    @Test
    void adminCanAssignRoleToUser() {
        // Given
        Role guestRole = new Role(RoleName.GUEST);
        Role architectRole = new Role(RoleName.ARCHITECT);
        User targetUser = new User("target@example.com", "hash", guestRole);

        when(userRepository.findById(1L)).thenReturn(Optional.of(targetUser));
        when(roleRepository.findByName(RoleName.ARCHITECT)).thenReturn(Optional.of(architectRole));
        when(refreshTokenRepository.findByUserAndRevokedFalse(targetUser)).thenReturn(Collections.emptyList());
        when(userRepository.save(any(User.class))).thenAnswer(inv -> inv.getArgument(0));

        // When
        User updated = service.assignRole(RoleName.ADMIN, 1L, RoleName.ARCHITECT);

        // Then — single role enforced (Req 2.1): user has exactly one role
        assertThat(updated.getRole().getName()).isEqualTo(RoleName.ARCHITECT);
        verify(userRepository).save(targetUser);
    }

    @Test
    void nonAdminRejectedWhenAssigningRole() {
        // Requirement 2.7: only Admin can assign roles
        assertThatThrownBy(() ->
                service.assignRole(RoleName.ARCHITECT, 1L, RoleName.DEVELOPER))
                .isInstanceOf(AdminOnlyOperationException.class)
                .hasMessageContaining("Only Admin");

        // No user lookup or save should have occurred
        verify(userRepository, never()).findById(any());
        verify(userRepository, never()).save(any());
    }

    @Test
    void nonAdminDeveloperRejected() {
        assertThatThrownBy(() ->
                service.assignRole(RoleName.DEVELOPER, 1L, RoleName.CLIENT))
                .isInstanceOf(AdminOnlyOperationException.class);
    }

    @Test
    void nonAdminClientRejected() {
        assertThatThrownBy(() ->
                service.assignRole(RoleName.CLIENT, 1L, RoleName.GUEST))
                .isInstanceOf(AdminOnlyOperationException.class);
    }

    @Test
    void nonAdminGuestRejected() {
        assertThatThrownBy(() ->
                service.assignRole(RoleName.GUEST, 1L, RoleName.ADMIN))
                .isInstanceOf(AdminOnlyOperationException.class);
    }

    @Test
    void nonAdminProductManagerRejected() {
        assertThatThrownBy(() ->
                service.assignRole(RoleName.PRODUCT_MANAGER_ROLE, 1L, RoleName.ADMIN))
                .isInstanceOf(AdminOnlyOperationException.class);
    }

    @Test
    void refreshTokensRevokedOnRoleChange() {
        // Given
        Role guestRole = new Role(RoleName.GUEST);
        Role devRole = new Role(RoleName.DEVELOPER);
        User targetUser = new User("user@example.com", "hash", guestRole);

        Instant now = Instant.now();
        RefreshToken token1 = new RefreshToken(targetUser, "hash1", now, now.plusSeconds(3600));
        RefreshToken token2 = new RefreshToken(targetUser, "hash2", now, now.plusSeconds(7200));

        when(userRepository.findById(2L)).thenReturn(Optional.of(targetUser));
        when(roleRepository.findByName(RoleName.DEVELOPER)).thenReturn(Optional.of(devRole));
        when(refreshTokenRepository.findByUserAndRevokedFalse(targetUser))
                .thenReturn(List.of(token1, token2));
        when(refreshTokenRepository.saveAll(any())).thenReturn(List.of(token1, token2));
        when(userRepository.save(any(User.class))).thenAnswer(inv -> inv.getArgument(0));

        // When
        service.assignRole(RoleName.ADMIN, 2L, RoleName.DEVELOPER);

        // Then — both tokens are revoked (Req 2.13, 2.14)
        assertThat(token1.isRevoked()).isTrue();
        assertThat(token2.isRevoked()).isTrue();
        verify(refreshTokenRepository).saveAll(List.of(token1, token2));
    }

    @Test
    void noRevocationWhenRoleUnchanged() {
        // Given: user already has ARCHITECT role, assigning ARCHITECT again
        Role architectRole = new Role(RoleName.ARCHITECT);
        User targetUser = new User("user@example.com", "hash", architectRole);

        when(userRepository.findById(3L)).thenReturn(Optional.of(targetUser));
        when(roleRepository.findByName(RoleName.ARCHITECT)).thenReturn(Optional.of(architectRole));

        // When
        User result = service.assignRole(RoleName.ADMIN, 3L, RoleName.ARCHITECT);

        // Then — no tokens revoked, user returned unchanged
        assertThat(result.getRole().getName()).isEqualTo(RoleName.ARCHITECT);
        verify(refreshTokenRepository, never()).findByUserAndRevokedFalse(any());
        verify(userRepository, never()).save(any());
    }

    @Test
    void throwsWhenTargetUserNotFound() {
        when(userRepository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() ->
                service.assignRole(RoleName.ADMIN, 99L, RoleName.DEVELOPER))
                .isInstanceOf(UserNotFoundException.class)
                .hasMessageContaining("99");
    }

    @Test
    void throwsWhenRoleNotFound() {
        Role guestRole = new Role(RoleName.GUEST);
        User targetUser = new User("user@example.com", "hash", guestRole);
        when(userRepository.findById(1L)).thenReturn(Optional.of(targetUser));
        when(roleRepository.findByName(RoleName.DEVELOPER)).thenReturn(Optional.empty());

        assertThatThrownBy(() ->
                service.assignRole(RoleName.ADMIN, 1L, RoleName.DEVELOPER))
                .isInstanceOf(RoleNotFoundException.class);
    }

    @Test
    void singleRoleEnforced_userNeverHasMultipleRoles() {
        // Verify the invariant: after assignment, the user has exactly one role
        Role guestRole = new Role(RoleName.GUEST);
        Role adminRole = new Role(RoleName.ADMIN);
        User targetUser = new User("multi@example.com", "hash", guestRole);

        when(userRepository.findById(5L)).thenReturn(Optional.of(targetUser));
        when(roleRepository.findByName(RoleName.ADMIN)).thenReturn(Optional.of(adminRole));
        when(refreshTokenRepository.findByUserAndRevokedFalse(targetUser)).thenReturn(Collections.emptyList());
        when(userRepository.save(any(User.class))).thenAnswer(inv -> inv.getArgument(0));

        User updated = service.assignRole(RoleName.ADMIN, 5L, RoleName.ADMIN);

        // Single role: the user's previous GUEST role is replaced, not accumulated.
        assertThat(updated.getRole().getName()).isEqualTo(RoleName.ADMIN);
        // The User entity has a ManyToOne relationship to Role, so exactly one.
    }
}
