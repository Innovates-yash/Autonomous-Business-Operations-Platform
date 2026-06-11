package com.aisa.auth.web;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.aisa.auth.service.AuthorizationService;
import com.aisa.auth.web.dto.AuthorizeResponse;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.FilterType;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

/**
 * Web-layer tests for {@link AuthorizationController} verifying the HTTP contract
 * for the authorization decision endpoint (POST /api/auth/authorize).
 *
 * - Returns PERMIT when the service grants access (Req 2.3)
 * - Returns DENY when the service denies access (Req 2.3, 2.5)
 * - Responds within 500ms (Req 2.4) — inherent in mocked service test
 * - Returns 400 on validation failures (missing fields)
 */
@WebMvcTest(value = AuthorizationController.class,
        excludeFilters = @ComponentScan.Filter(
                type = FilterType.ASSIGNABLE_TYPE,
                classes = AuthExceptionHandler.class))
class AuthorizationControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private AuthorizationService authorizationService;

    @Test
    void returnsPermitWhenServiceGrants() throws Exception {
        when(authorizationService.decide(1L, "blueprint.create"))
                .thenReturn(AuthorizeResponse.permit());

        mockMvc.perform(post("/api/auth/authorize")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"userId":1,"permission":"blueprint.create"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.decision").value("PERMIT"));
    }

    @Test
    void returnsDenyWhenServiceDenies() throws Exception {
        when(authorizationService.decide(2L, "admin.manage"))
                .thenReturn(AuthorizeResponse.deny());

        mockMvc.perform(post("/api/auth/authorize")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"userId":2,"permission":"admin.manage"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.decision").value("DENY"));
    }

    @Test
    void returnsDenyWhenUserNotFound() throws Exception {
        when(authorizationService.decide(99L, "project.create"))
                .thenReturn(AuthorizeResponse.deny());

        mockMvc.perform(post("/api/auth/authorize")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"userId":99,"permission":"project.create"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.decision").value("DENY"));
    }

    @Test
    void returns400WhenUserIdMissing() throws Exception {
        mockMvc.perform(post("/api/auth/authorize")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"permission":"blueprint.create"}
                                """))
                .andExpect(status().isBadRequest());
    }

    @Test
    void returns400WhenPermissionMissing() throws Exception {
        mockMvc.perform(post("/api/auth/authorize")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"userId":1}
                                """))
                .andExpect(status().isBadRequest());
    }

    @Test
    void returns400WhenBodyEmpty() throws Exception {
        mockMvc.perform(post("/api/auth/authorize")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void decisionResponseWithin500ms() throws Exception {
        when(authorizationService.decide(5L, "project.edit"))
                .thenReturn(AuthorizeResponse.permit());

        long start = System.currentTimeMillis();

        mockMvc.perform(post("/api/auth/authorize")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"userId":5,"permission":"project.edit"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.decision").value("PERMIT"));

        long elapsed = System.currentTimeMillis() - start;
        // Requirement 2.4: decision within 500ms
        assert elapsed < 500 : "Decision took " + elapsed + "ms, exceeds 500ms contract";
    }
}
