package com.aisa.project.web;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.aisa.commons.domain.ProjectState;
import com.aisa.project.domain.Idea;
import com.aisa.project.domain.Project;
import com.aisa.project.domain.Requirement;
import com.aisa.project.domain.RequirementType;
import com.aisa.project.domain.UseCase;
import com.aisa.project.security.ProjectPrincipal;
import com.aisa.project.service.RequirementAnalysisService;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.web.servlet.MockMvc;

/**
 * Web-layer tests for {@link AnalysisController}: validates permission enforcement
 * (REQUIREMENT_ANALYSIS_START permission via X-User-Role) and successful analysis
 * response structure (Requirements 4.1, 4.2, 4.5, 4.6).
 */
@WebMvcTest(AnalysisController.class)
class AnalysisControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private RequirementAnalysisService analysisService;

    private final UUID ownerId = UUID.randomUUID();
    private final UUID projectId = UUID.randomUUID();

    private Project analyzedProject() {
        Project project = new Project("Test", "Build a Food Delivery App", ownerId);
        project.setState(ProjectState.ANALYZING);
        Idea idea = new Idea("Test", "Build a Food Delivery App");
        idea.setProject(project);
        project.setIdea(idea);

        Requirement fr = new Requirement(project, "Users can order food", RequirementType.FUNCTIONAL);
        Requirement nfr = new Requirement(project, "Respond within 2s", RequirementType.NON_FUNCTIONAL);
        project.getRequirements().add(fr);
        project.getRequirements().add(nfr);

        UseCase uc = new UseCase(project, "Order Food", "User orders food online");
        uc.getRequirements().add(fr);
        project.getUseCases().add(uc);

        return project;
    }

    @Test
    void analyzeSucceedsForProductManager() throws Exception {
        when(analysisService.analyzeProject(eq(projectId), any(ProjectPrincipal.class)))
                .thenReturn(analyzedProject());

        mockMvc.perform(post("/api/projects/" + projectId + "/analyze")
                        .header(AnalysisController.USER_ID_HEADER, ownerId.toString())
                        .header(AnalysisController.USER_ROLE_HEADER, "PRODUCT_MANAGER"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.projectState").value("ANALYZING"))
                .andExpect(jsonPath("$.requirements").isArray())
                .andExpect(jsonPath("$.requirements.length()").value(2))
                .andExpect(jsonPath("$.useCases").isArray())
                .andExpect(jsonPath("$.useCases.length()").value(1));
    }

    @Test
    void analyzeSucceedsForAdmin() throws Exception {
        when(analysisService.analyzeProject(eq(projectId), any(ProjectPrincipal.class)))
                .thenReturn(analyzedProject());

        mockMvc.perform(post("/api/projects/" + projectId + "/analyze")
                        .header(AnalysisController.USER_ID_HEADER, ownerId.toString())
                        .header(AnalysisController.USER_ROLE_HEADER, "ADMIN"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.projectState").value("ANALYZING"));
    }

    @Test
    void analyzeSucceedsForArchitect() throws Exception {
        when(analysisService.analyzeProject(eq(projectId), any(ProjectPrincipal.class)))
                .thenReturn(analyzedProject());

        mockMvc.perform(post("/api/projects/" + projectId + "/analyze")
                        .header(AnalysisController.USER_ID_HEADER, ownerId.toString())
                        .header(AnalysisController.USER_ROLE_HEADER, "ARCHITECT"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.projectState").value("ANALYZING"));
    }

    @Test
    void analyzeSucceedsForClient() throws Exception {
        when(analysisService.analyzeProject(eq(projectId), any(ProjectPrincipal.class)))
                .thenReturn(analyzedProject());

        mockMvc.perform(post("/api/projects/" + projectId + "/analyze")
                        .header(AnalysisController.USER_ID_HEADER, ownerId.toString())
                        .header(AnalysisController.USER_ROLE_HEADER, "CLIENT"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.projectState").value("ANALYZING"));
    }

    @Test
    void analyzeRejectedForDeveloperRole() throws Exception {
        // DEVELOPER does not have REQUIREMENT_ANALYSIS_START permission.
        mockMvc.perform(post("/api/projects/" + projectId + "/analyze")
                        .header(AnalysisController.USER_ID_HEADER, ownerId.toString())
                        .header(AnalysisController.USER_ROLE_HEADER, "DEVELOPER"))
                .andExpect(status().isForbidden());

        // Service must NOT be called.
        verify(analysisService, never()).analyzeProject(any(), any());
    }

    @Test
    void analyzeRejectedForGuestRole() throws Exception {
        // GUEST does not have REQUIREMENT_ANALYSIS_START permission.
        mockMvc.perform(post("/api/projects/" + projectId + "/analyze")
                        .header(AnalysisController.USER_ID_HEADER, ownerId.toString())
                        .header(AnalysisController.USER_ROLE_HEADER, "GUEST"))
                .andExpect(status().isForbidden());

        verify(analysisService, never()).analyzeProject(any(), any());
    }

    @Test
    void analyzeRejectedWithoutPrincipalHeader() throws Exception {
        // Missing X-User-Id should result in 401.
        mockMvc.perform(post("/api/projects/" + projectId + "/analyze"))
                .andExpect(status().isUnauthorized());

        verify(analysisService, never()).analyzeProject(any(), any());
    }
}
