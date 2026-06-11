package com.aisa.audit.web;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.aisa.audit.domain.AuditAction;
import com.aisa.audit.domain.AuditEvent;
import com.aisa.audit.query.AuditQueryService;
import com.aisa.audit.security.AuditPrincipal;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Pageable;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

/**
 * Web-layer tests for {@link AuditQueryController}: Admin-only filtering, empty
 * result on no match, non-Admin denial, and rejection of modification/deletion
 * attempts against the immutable audit store (Req 23.4–23.7).
 */
@ExtendWith(MockitoExtension.class)
class AuditQueryControllerTest {

    @Mock
    private AuditQueryService queryService;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders
                .standaloneSetup(new AuditQueryController(queryService))
                .setControllerAdvice(new AuditExceptionHandler())
                .build();
    }

    private static AuditEvent event() {
        return new AuditEvent("user-7", AuditAction.AUTHENTICATION, "target-1",
                Instant.parse("2024-01-01T00:00:00.123Z"), "corr-1");
    }

    @Test
    void adminReceivesMatchingEvents() throws Exception {
        when(queryService.query(any(AuditPrincipal.class), any(), any(Pageable.class)))
                .thenReturn(List.of(event()));

        mockMvc.perform(get("/api/audit/events")
                        .header("X-User-Id", "admin-1")
                        .header("X-User-Role", "ADMIN")
                        .param("userId", "user-7")
                        .param("action", "AUTHENTICATION"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", org.hamcrest.Matchers.hasSize(1)))
                .andExpect(jsonPath("$[0].userId").value("user-7"))
                .andExpect(jsonPath("$[0].action").value("AUTHENTICATION"));
    }

    @Test
    void adminReceivesEmptyArrayWhenNothingMatches() throws Exception {
        when(queryService.query(any(AuditPrincipal.class), any(), any(Pageable.class)))
                .thenReturn(List.of());

        mockMvc.perform(get("/api/audit/events")
                        .header("X-User-Id", "admin-1")
                        .header("X-User-Role", "ADMIN")
                        .param("userId", "ghost"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", org.hamcrest.Matchers.hasSize(0)));
    }

    @Test
    void nonAdminIsDeniedWithAuthorizationError() throws Exception {
        // A non-Admin role reaches the service, which throws; here we assert the
        // controller surfaces the shared AUTHORIZATION_DENIED contract.
        when(queryService.query(any(AuditPrincipal.class), any(), any(Pageable.class)))
                .thenThrow(new com.aisa.audit.query.AuthorizationDeniedException("denied"));

        mockMvc.perform(get("/api/audit/events")
                        .header("X-User-Id", "client-1")
                        .header("X-User-Role", "CLIENT"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("AUTHORIZATION_DENIED"));
    }

    @Test
    void missingPrincipalIsRejected() throws Exception {
        mockMvc.perform(get("/api/audit/events"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("AUTHORIZATION_DENIED"));

        verify(queryService, never()).query(any(), any(), any());
    }

    @Test
    void modificationAttemptIsRejectedAsImmutable() throws Exception {
        mockMvc.perform(put("/api/audit/events")
                        .header("X-User-Id", "admin-1")
                        .header("X-User-Role", "ADMIN"))
                .andExpect(status().isMethodNotAllowed())
                .andExpect(jsonPath("$.code").value("AUDIT_IMMUTABLE"));

        verify(queryService, never()).query(any(), any(), any());
    }

    @Test
    void deletionAttemptIsRejectedAsImmutable() throws Exception {
        mockMvc.perform(delete("/api/audit/events")
                        .header("X-User-Id", "admin-1")
                        .header("X-User-Role", "ADMIN"))
                .andExpect(status().isMethodNotAllowed())
                .andExpect(jsonPath("$.code").value("AUDIT_IMMUTABLE"));

        verify(queryService, never()).query(any(), any(), any());
    }
}
