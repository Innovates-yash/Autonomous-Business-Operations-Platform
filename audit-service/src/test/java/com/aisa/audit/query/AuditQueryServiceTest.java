package com.aisa.audit.query;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.aisa.audit.domain.AuditAction;
import com.aisa.audit.domain.AuditEvent;
import com.aisa.audit.repository.AuditEventRepository;
import com.aisa.audit.security.AuditPrincipal;
import com.aisa.commons.domain.Role;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

/**
 * Unit tests for {@link AuditQueryService}: Admin-only filtered query with an
 * empty result on no match and denial of non-Admin callers (Req 23.4–23.6).
 */
@ExtendWith(MockitoExtension.class)
class AuditQueryServiceTest {

    @Mock
    private AuditEventRepository repository;

    private final Pageable pageable = PageRequest.of(0, 50);

    private AuditQueryService service() {
        return new AuditQueryService(repository);
    }

    private static AuditPrincipal admin() {
        return new AuditPrincipal("admin-1", Role.ADMIN);
    }

    private static AuditEvent event(String userId, AuditAction action) {
        return new AuditEvent(userId, action, "target-1",
                Instant.parse("2024-01-01T00:00:00.123Z"), "corr-1");
    }

    @Test
    void adminQueryReturnsMatchingEventsAndPassesFiltersThrough() {
        AuditQueryFilter filter = new AuditQueryFilter(
                "user-7", AuditAction.ROLE_CHANGE,
                Instant.parse("2024-01-01T00:00:00Z"),
                Instant.parse("2024-01-31T23:59:59Z"));
        List<AuditEvent> matches = List.of(
                event("user-7", AuditAction.ROLE_CHANGE),
                event("user-7", AuditAction.ROLE_CHANGE));
        when(repository.search(eq("user-7"), eq(AuditAction.ROLE_CHANGE),
                any(Instant.class), any(Instant.class), eq(pageable)))
                .thenReturn(matches);

        List<AuditEvent> result = service().query(admin(), filter, pageable);

        assertThat(result).hasSize(2);

        ArgumentCaptor<String> userIdCaptor = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<AuditAction> actionCaptor = ArgumentCaptor.forClass(AuditAction.class);
        verify(repository).search(userIdCaptor.capture(), actionCaptor.capture(),
                any(Instant.class), any(Instant.class), eq(pageable));
        assertThat(userIdCaptor.getValue()).isEqualTo("user-7");
        assertThat(actionCaptor.getValue()).isEqualTo(AuditAction.ROLE_CHANGE);
    }

    @Test
    void adminQueryReturnsEmptySetWhenNothingMatches() {
        AuditQueryFilter filter = new AuditQueryFilter(
                "no-such-user", null, null, null);
        when(repository.search(eq("no-such-user"), eq(null), eq(null), eq(null), eq(pageable)))
                .thenReturn(List.of());

        List<AuditEvent> result = service().query(admin(), filter, pageable);

        assertThat(result).isEmpty();
    }

    @Test
    void nonAdminCallerIsDeniedAndNoEventsAreRead() {
        AuditPrincipal client = new AuditPrincipal("client-1", Role.CLIENT);
        AuditQueryFilter filter = new AuditQueryFilter(null, null, null, null);

        assertThatThrownBy(() -> service().query(client, filter, pageable))
                .isInstanceOf(AuthorizationDeniedException.class);

        // The denial happens before any read of the audit store (Req 23.6).
        verifyNoInteractions(repository);
    }

    @Test
    void everyNonAdminRoleIsDenied() {
        AuditQueryFilter filter = new AuditQueryFilter(null, null, null, null);
        AuditQueryService service = service();

        for (Role role : Role.values()) {
            if (role == Role.ADMIN) {
                continue;
            }
            AuditPrincipal principal = new AuditPrincipal("caller", role);
            assertThatThrownBy(() -> service.query(principal, filter, pageable))
                    .as("role %s must be denied", role)
                    .isInstanceOf(AuthorizationDeniedException.class);
        }

        verify(repository, never()).search(any(), any(), any(), any(), any());
    }
}
