package com.aisa.auth.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.aisa.auth.domain.Permission;
import com.aisa.auth.domain.Role;
import com.aisa.auth.domain.RoleName;
import com.aisa.auth.domain.User;
import com.aisa.auth.repository.UserRepository;
import com.aisa.auth.web.dto.AuthorizeResponse;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * Unit tests for {@link AuthorizationService} verifying:
 * - PERMIT when the user's role holds the required permission (Req 2.3)
 * - DENY when the user's role does not hold the required permission (Req 2.3, 2.5)
 * - DENY when the user has no assigned role (Req 2.6)
 * - DENY when the user does not exist (Req 2.6)
 * - No state change on DENY (Req 2.5) — verified via read-only interactions
 * - Response within contract time (Req 2.4) — verified by timing assertion
 */
@ExtendWith(MockitoExtension.class)
class AuthorizationServiceTest {

    @Mock
    private UserRepository userRepository;

    private AuthorizationService service;

    @BeforeEach
    void setUp() {
        service = new AuthorizationService(userRepository);
    }

    @Test
    void permitWhenRoleHoldsRequiredPermission() {
        // Given: user has ARCHITECT role with "blueprint.create" permission
        Permission createPermission = new Permission("blueprint.create", "Create blueprints");
        Role architectRole = new Role(RoleName.ARCHITECT);
        architectRole.setPermissions(Set.of(createPermission));
        User user = new User("architect@example.com", "hash", architectRole);

        when(userRepository.findById(1L)).thenReturn(Optional.of(user));

        // When
        AuthorizeResponse response = service.decide(1L, "blueprint.create");

        // Then — Requirement 2.3: permit when role holds the permission
        assertThat(response.decision()).isEqualTo(AuthorizeResponse.PERMIT);
    }

    @Test
    void denyWhenRoleDoesNotHoldRequiredPermission() {
        // Given: user has DEVELOPER role without "blueprint.create" permission
        Permission readPermission = new Permission("blueprint.read", "Read blueprints");
        Role developerRole = new Role(RoleName.DEVELOPER);
        developerRole.setPermissions(Set.of(readPermission));
        User user = new User("dev@example.com", "hash", developerRole);

        when(userRepository.findById(2L)).thenReturn(Optional.of(user));

        // When
        AuthorizeResponse response = service.decide(2L, "blueprint.create");

        // Then — Requirement 2.3, 2.5: deny, no state change
        assertThat(response.decision()).isEqualTo(AuthorizeResponse.DENY);
    }

    @Test
    void denyWhenUserHasNoRole() {
        // Given: user with null role (edge case for Req 2.6)
        User user = new User("norole@example.com", "hash", null);

        when(userRepository.findById(3L)).thenReturn(Optional.of(user));

        // When
        AuthorizeResponse response = service.decide(3L, "project.create");

        // Then — Requirement 2.6: deny when no role assigned
        assertThat(response.decision()).isEqualTo(AuthorizeResponse.DENY);
    }

    @Test
    void denyWhenUserDoesNotExist() {
        // Given: no user found
        when(userRepository.findById(99L)).thenReturn(Optional.empty());

        // When
        AuthorizeResponse response = service.decide(99L, "project.create");

        // Then — Requirement 2.6: deny when user not found (no role)
        assertThat(response.decision()).isEqualTo(AuthorizeResponse.DENY);
    }

    @Test
    void noStateChangeOnDeny() {
        // Given: user without the required permission
        Permission readPermission = new Permission("blueprint.read", "Read blueprints");
        Role guestRole = new Role(RoleName.GUEST);
        guestRole.setPermissions(Set.of(readPermission));
        User user = new User("guest@example.com", "hash", guestRole);

        when(userRepository.findById(4L)).thenReturn(Optional.of(user));

        // When: request a permission the user does not hold
        AuthorizeResponse response = service.decide(4L, "admin.manage");

        // Then — Requirement 2.5: no writes, no saves, no side effects on deny
        assertThat(response.decision()).isEqualTo(AuthorizeResponse.DENY);
        verify(userRepository, never()).save(user);
        verify(userRepository, never()).delete(user);
    }

    @Test
    void decisionReturnedWithinContractTime() {
        // Given: user with a permission set
        Permission perm = new Permission("project.create", "Create projects");
        Role role = new Role(RoleName.PRODUCT_MANAGER_ROLE);
        role.setPermissions(Set.of(perm));
        User user = new User("pm@example.com", "hash", role);

        when(userRepository.findById(5L)).thenReturn(Optional.of(user));

        // When: time the decision
        long start = System.currentTimeMillis();
        AuthorizeResponse response = service.decide(5L, "project.create");
        long elapsed = System.currentTimeMillis() - start;

        // Then — Requirement 2.4: decision within 500ms
        assertThat(response.decision()).isEqualTo(AuthorizeResponse.PERMIT);
        assertThat(elapsed).isLessThan(500L);
    }

    @Test
    void denyWhenRoleHasEmptyPermissions() {
        // Given: user has a role but zero permissions assigned
        Role clientRole = new Role(RoleName.CLIENT);
        clientRole.setPermissions(Set.of());
        User user = new User("client@example.com", "hash", clientRole);

        when(userRepository.findById(6L)).thenReturn(Optional.of(user));

        // When
        AuthorizeResponse response = service.decide(6L, "blueprint.approve");

        // Then — deny because role has no permissions at all
        assertThat(response.decision()).isEqualTo(AuthorizeResponse.DENY);
    }

    @Test
    void permitWithMultiplePermissions() {
        // Given: role has multiple permissions, one of which matches
        Permission p1 = new Permission("project.create", "Create projects");
        Permission p2 = new Permission("project.edit", "Edit projects");
        Permission p3 = new Permission("blueprint.read", "Read blueprints");
        Role pmRole = new Role(RoleName.PRODUCT_MANAGER_ROLE);
        pmRole.setPermissions(Set.of(p1, p2, p3));
        User user = new User("pm@example.com", "hash", pmRole);

        when(userRepository.findById(7L)).thenReturn(Optional.of(user));

        // When: requesting a permission that exists in the set
        AuthorizeResponse response = service.decide(7L, "project.edit");

        // Then — PERMIT
        assertThat(response.decision()).isEqualTo(AuthorizeResponse.PERMIT);
    }
}
