package com.aisa.auth.web;

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
 * Web-layer tests for {@link RoleAssignmentController} at
 * {@code POST /api/auth/roles/assign} verifying:
 * - Admin role assignment returns 200 (Requirements 2.1, 2.13)
 * - Non-Admin callers receive 403 (Requirement 2.7)
 * - Invalid role names receive 400
 * - Missing fields receive 400
 */
@WebMvcTest(RoleAssignmentController.class)
class RoleAssignmentControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private RoleAssignmentService roleAssignmentService;

    @Test
    void adminAssignsRoleSuccessfully() throws Exception {
        Role devRole = new Role(RoleName.DEVELOPER);
        User updatedUser = new User("target@example.com", "hash", devRole);

        when(roleAssignmentService.assignRole(eq(RoleName.ADMIN), eq(1L), eq(RoleName.DEVELOPER)))
                .thenReturn(updatedUser);

        mockMvc.perform(post("/api/auth/roles/assign")
                        .header("X-User-Role", "ADMIN")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"userId":1,"targetRole":"DEVELOPER"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.email").value("target@example.com"))
                .andExpect(jsonPath("$.role").value("DEVELOPER"));
    }

    @Test
    void nonAdminCallerReceives403() throws Exception {
        when(roleAssignmentService.assignRole(eq(RoleName.ARCHITECT), eq(1L), eq(RoleName.DEVELOPER)))
                .thenThrow(new AdminOnlyOperationException("Only Admin users may assign roles"));

        mockMvc.perform(post("/api/auth/roles/assign")
                        .header("X-User-Role", "ARCHITECT")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"userId":1,"targetRole":"DEVELOPER"}
                                """))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value(ErrorCodes.AUTHORIZATION_DENIED));
    }

    @Test
    void guestCallerReceives403() throws Exception {
        when(roleAssignmentService.assignRole(eq(RoleName.GUEST), eq(2L), eq(RoleName.CLIENT)))
                .thenThrow(new AdminOnlyOperationException("Only Admin users may assign roles"));

        mockMvc.perform(post("/api/auth/roles/assign")
                        .header("X-User-Role", "GUEST")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"userId":2,"targetRole":"CLIENT"}
                                """))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value(ErrorCodes.AUTHORIZATION_DENIED));
    }

    @Test
    void invalidTargetRoleReceives400() throws Exception {
        mockMvc.perform(post("/api/auth/roles/assign")
                        .header("X-User-Role", "ADMIN")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"userId":1,"targetRole":"INVALID_ROLE"}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(ErrorCodes.VALIDATION_ERROR));
    }

    @Test
    void missingUserIdReceives400() throws Exception {
        mockMvc.perform(post("/api/auth/roles/assign")
                        .header("X-User-Role", "ADMIN")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"targetRole":"DEVELOPER"}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(ErrorCodes.VALIDATION_ERROR));
    }

    @Test
    void missingTargetRoleReceives400() throws Exception {
        mockMvc.perform(post("/api/auth/roles/assign")
                        .header("X-User-Role", "ADMIN")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"userId":1}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(ErrorCodes.VALIDATION_ERROR));
    }

    @Test
    void missingCallerRoleHeaderReceives400() throws Exception {
        mockMvc.perform(post("/api/auth/roles/assign")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"userId":1,"targetRole":"DEVELOPER"}
                                """))
                .andExpect(status().isBadRequest());
    }

    @Test
    void nonExistentUserReceives404() throws Exception {
        when(roleAssignmentService.assignRole(eq(RoleName.ADMIN), eq(99L), eq(RoleName.DEVELOPER)))
                .thenThrow(new UserNotFoundException("User not found: 99"));

        mockMvc.perform(post("/api/auth/roles/assign")
                        .header("X-User-Role", "ADMIN")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"userId":99,"targetRole":"DEVELOPER"}
                                """))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value(ErrorCodes.NOT_FOUND));
    }
}
