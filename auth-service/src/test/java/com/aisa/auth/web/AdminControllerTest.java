package com.aisa.auth.web;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.aisa.auth.domain.Role;
import com.aisa.auth.domain.RoleName;
import com.aisa.auth.domain.User;
import com.aisa.auth.service.AdminOnlyOperationException;
import com.aisa.auth.service.RoleAssignmentService;
import com.aisa.auth.service.UserNotFoundException;
import com.aisa.commons.error.ErrorCodes;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

/**
 * Web-layer tests for {@link AdminController} verifying:
 * - Admin role assignment returns 200 (Requirements 2.1, 2.13)
 * - Non-Admin callers receive 403 (Requirement 2.7)
 * - Invalid role names receive 400
 * - Non-existent user receives 404
 * - POST /api/admin/roles/assign endpoint (Requirements 2.7, 2.13, 2.14)
 */
@WebMvcTest(AdminController.class)
class AdminControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private RoleAssignmentService roleAssignmentService;

    // ===== Tests for POST /api/admin/users/{userId}/role =====

    @Test
    void adminAssignsRoleSuccessfully() throws Exception {
        Role devRole = new Role(RoleName.DEVELOPER);
        User updatedUser = new User("target@example.com", "hash", devRole);

        when(roleAssignmentService.assignRole(eq(RoleName.ADMIN), eq(1L), eq(RoleName.DEVELOPER)))
                .thenReturn(updatedUser);

        mockMvc.perform(post("/api/admin/users/1/role")
                        .header("X-User-Role", "ADMIN")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"role":"DEVELOPER"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.email").value("target@example.com"))
                .andExpect(jsonPath("$.role").value("DEVELOPER"));
    }

    @Test
    void nonAdminCallerReceives403() throws Exception {
        when(roleAssignmentService.assignRole(eq(RoleName.ARCHITECT), any(), any()))
                .thenThrow(new AdminOnlyOperationException("Only Admin users may assign roles"));

        mockMvc.perform(post("/api/admin/users/1/role")
                        .header("X-User-Role", "ARCHITECT")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"role":"DEVELOPER"}
                                """))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value(ErrorCodes.AUTHORIZATION_DENIED));
    }

    @Test
    void invalidRoleNameReceives400() throws Exception {
        mockMvc.perform(post("/api/admin/users/1/role")
                        .header("X-User-Role", "ADMIN")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"role":"INVALID_ROLE"}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(ErrorCodes.VALIDATION_ERROR));
    }

    @Test
    void nonExistentUserReceives404() throws Exception {
        when(roleAssignmentService.assignRole(eq(RoleName.ADMIN), eq(99L), eq(RoleName.DEVELOPER)))
                .thenThrow(new UserNotFoundException("User not found: 99"));

        mockMvc.perform(post("/api/admin/users/99/role")
                        .header("X-User-Role", "ADMIN")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"role":"DEVELOPER"}
                                """))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value(ErrorCodes.NOT_FOUND));
    }

    @Test
    void missingRoleFieldReceives400() throws Exception {
        mockMvc.perform(post("/api/admin/users/1/role")
                        .header("X-User-Role", "ADMIN")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"role":null}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(ErrorCodes.VALIDATION_ERROR));
    }

    @Test
    void missingCallerRoleHeaderReceives400() throws Exception {
        mockMvc.perform(post("/api/admin/users/1/role")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"role":"DEVELOPER"}
                                """))
                .andExpect(status().isBadRequest());
    }

    // ===== Tests for POST /api/admin/roles/assign =====

    @Test
    void assignEndpoint_adminAssignsRoleSuccessfully() throws Exception {
        Role architectRole = new Role(RoleName.ARCHITECT);
        User updatedUser = new User("user@example.com", "hash", architectRole);

        when(roleAssignmentService.assignRole(eq(RoleName.ADMIN), eq(5L), eq(RoleName.ARCHITECT)))
                .thenReturn(updatedUser);

        mockMvc.perform(post("/api/admin/roles/assign")
                        .header("X-User-Role", "ADMIN")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"userId":5,"targetRole":"ARCHITECT"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.email").value("user@example.com"))
                .andExpect(jsonPath("$.role").value("ARCHITECT"));
    }

    @Test
    void assignEndpoint_nonAdminDenied() throws Exception {
        when(roleAssignmentService.assignRole(eq(RoleName.DEVELOPER), eq(5L), eq(RoleName.CLIENT)))
                .thenThrow(new AdminOnlyOperationException("Only Admin users may assign roles"));

        mockMvc.perform(post("/api/admin/roles/assign")
                        .header("X-User-Role", "DEVELOPER")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"userId":5,"targetRole":"CLIENT"}
                                """))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value(ErrorCodes.AUTHORIZATION_DENIED));
    }

    @Test
    void assignEndpoint_missingUserIdReceives400() throws Exception {
        mockMvc.perform(post("/api/admin/roles/assign")
                        .header("X-User-Role", "ADMIN")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"targetRole":"DEVELOPER"}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(ErrorCodes.VALIDATION_ERROR));
    }

    @Test
    void assignEndpoint_invalidTargetRoleReceives400() throws Exception {
        mockMvc.perform(post("/api/admin/roles/assign")
                        .header("X-User-Role", "ADMIN")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"userId":1,"targetRole":"INVALID_ROLE"}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(ErrorCodes.VALIDATION_ERROR));
    }

    @Test
    void assignEndpoint_missingTargetRoleReceives400() throws Exception {
        mockMvc.perform(post("/api/admin/roles/assign")
                        .header("X-User-Role", "ADMIN")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"userId":1}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(ErrorCodes.VALIDATION_ERROR));
    }
}
