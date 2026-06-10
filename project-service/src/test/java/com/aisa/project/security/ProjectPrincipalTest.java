package com.aisa.project.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.aisa.commons.domain.Role;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/**
 * Unit tests for {@link ProjectPrincipal} header parsing and the view-scope rule
 * used for Requirement 3.6.
 */
class ProjectPrincipalTest {

    @Test
    void parsesUserIdAndRoleFromHeaders() {
        UUID id = UUID.randomUUID();
        ProjectPrincipal principal = ProjectPrincipal.from(id.toString(), "PRODUCT_MANAGER");

        assertThat(principal.userId()).isEqualTo(id);
        assertThat(principal.role()).isEqualTo(Role.PRODUCT_MANAGER);
    }

    @Test
    void defaultsToGuestWhenRoleHeaderAbsentOrUnknown() {
        UUID id = UUID.randomUUID();

        assertThat(ProjectPrincipal.from(id.toString(), null).role()).isEqualTo(Role.GUEST);
        assertThat(ProjectPrincipal.from(id.toString(), "NOT_A_ROLE").role()).isEqualTo(Role.GUEST);
    }

    @Test
    void rejectsMissingOrMalformedUserId() {
        assertThatThrownBy(() -> ProjectPrincipal.from(null, "ADMIN"))
                .isInstanceOf(MissingPrincipalException.class);
        assertThatThrownBy(() -> ProjectPrincipal.from("  ", "ADMIN"))
                .isInstanceOf(MissingPrincipalException.class);
        assertThatThrownBy(() -> ProjectPrincipal.from("not-a-uuid", "ADMIN"))
                .isInstanceOf(MissingPrincipalException.class);
    }

    @Test
    void onlyAdminAndArchitectCanViewAllProjects() {
        UUID id = UUID.randomUUID();
        assertThat(new ProjectPrincipal(id, Role.ADMIN).canViewAllProjects()).isTrue();
        assertThat(new ProjectPrincipal(id, Role.ARCHITECT).canViewAllProjects()).isTrue();
        assertThat(new ProjectPrincipal(id, Role.PRODUCT_MANAGER).canViewAllProjects()).isFalse();
        assertThat(new ProjectPrincipal(id, Role.DEVELOPER).canViewAllProjects()).isFalse();
        assertThat(new ProjectPrincipal(id, Role.CLIENT).canViewAllProjects()).isFalse();
        assertThat(new ProjectPrincipal(id, Role.GUEST).canViewAllProjects()).isFalse();
    }
}
