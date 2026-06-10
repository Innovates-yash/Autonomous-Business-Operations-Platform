package com.aisa.project.web;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.aisa.commons.domain.ProjectState;
import com.aisa.commons.error.ErrorCodes;
import com.aisa.project.domain.Project;
import com.aisa.project.security.ProjectPrincipal;
import com.aisa.project.service.ProjectNotFoundException;
import com.aisa.project.service.ProjectService;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

/**
 * Web-layer tests for {@link ProjectController} and {@link ProjectExceptionHandler}:
 * creation success (Requirements 3.1, 3.2), field-level validation errors
 * (Requirement 3.11), update persistence (Requirement 3.3), and not-found for
 * absent or inaccessible Projects (Requirements 3.6, 3.7).
 */
@WebMvcTest(ProjectController.class)
class ProjectControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private ProjectService projectService;

    private final UUID ownerId = UUID.randomUUID();

    private Project sampleProject(String name, String description) {
        return new Project(name, description, ownerId);
    }

    @Test
    void createsValidProjectAndReturns201() throws Exception {
        when(projectService.create(any(), any())).thenReturn(
                sampleProject("Food Delivery", "An app for ordering meals"));

        mockMvc.perform(post("/api/projects")
                        .header(ProjectController.USER_ID_HEADER, ownerId.toString())
                        .header(ProjectController.USER_ROLE_HEADER, "PRODUCT_MANAGER")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"Food Delivery","description":"An app for ordering meals"}
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.name").value("Food Delivery"))
                .andExpect(jsonPath("$.description").value("An app for ordering meals"))
                .andExpect(jsonPath("$.state").value(ProjectState.DRAFT.name()))
                .andExpect(jsonPath("$.ownerId").value(ownerId.toString()));
    }

    @Test
    void rejectsBlankNameWithFieldError() throws Exception {
        mockMvc.perform(post("/api/projects")
                        .header(ProjectController.USER_ID_HEADER, ownerId.toString())
                        .header(ProjectController.USER_ROLE_HEADER, "PRODUCT_MANAGER")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"","description":"A valid description"}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(ErrorCodes.VALIDATION_ERROR))
                .andExpect(jsonPath("$.fieldErrors[?(@.field=='name')]").exists());
    }

    @Test
    void rejectsOverlongDescriptionWithFieldError() throws Exception {
        String tooLong = "x".repeat(5001);
        mockMvc.perform(post("/api/projects")
                        .header(ProjectController.USER_ID_HEADER, ownerId.toString())
                        .header(ProjectController.USER_ROLE_HEADER, "PRODUCT_MANAGER")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Valid\",\"description\":\"" + tooLong + "\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(ErrorCodes.VALIDATION_ERROR))
                .andExpect(jsonPath("$.fieldErrors[?(@.field=='description')]").exists());
    }

    @Test
    void updatePersistsAndReturns200() throws Exception {
        UUID projectId = UUID.randomUUID();
        when(projectService.update(eq(projectId), any(), any()))
                .thenReturn(sampleProject("New", "New description"));

        mockMvc.perform(put("/api/projects/" + projectId)
                        .header(ProjectController.USER_ID_HEADER, ownerId.toString())
                        .header(ProjectController.USER_ROLE_HEADER, "PRODUCT_MANAGER")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"New","description":"New description"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("New"))
                .andExpect(jsonPath("$.description").value("New description"));
    }

    @Test
    void getMissingProjectReturns404() throws Exception {
        UUID projectId = UUID.randomUUID();
        when(projectService.getById(eq(projectId), any(ProjectPrincipal.class)))
                .thenThrow(new ProjectNotFoundException(projectId));

        mockMvc.perform(get("/api/projects/" + projectId)
                        .header(ProjectController.USER_ID_HEADER, ownerId.toString())
                        .header(ProjectController.USER_ROLE_HEADER, "PRODUCT_MANAGER"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value(ErrorCodes.NOT_FOUND));
    }

    @Test
    void rejectsRequestWithoutPrincipalHeader() throws Exception {
        mockMvc.perform(get("/api/projects/" + UUID.randomUUID()))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value(ErrorCodes.AUTHORIZATION_DENIED));
    }
}
