package com.aisa.project.web;

import com.aisa.project.domain.Project;
import com.aisa.project.security.ProjectPrincipal;
import com.aisa.project.service.RequirementAnalysisService;
import com.aisa.project.web.dto.AnalysisResponse;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * HTTP endpoint for triggering requirement analysis on a Project
 * (Requirements 4.1, 4.2, 4.5, 4.6).
 *
 * <p>{@code POST /api/projects/{id}/analyze} initiates AI-driven analysis of the
 * Project's Idea, transitions the Project to {@code ANALYZING}, and returns the
 * generated requirements and use cases.
 */
@RestController
@RequestMapping("/api/projects")
public class AnalysisController {

    static final String USER_ID_HEADER = "X-User-Id";
    static final String USER_ROLE_HEADER = "X-User-Role";

    private final RequirementAnalysisService analysisService;

    public AnalysisController(RequirementAnalysisService analysisService) {
        this.analysisService = analysisService;
    }

    /**
     * Triggers requirement analysis for the specified Project. The Project must be
     * in the {@code DRAFT} state (only valid transition to ANALYZING). On success,
     * the Project transitions to {@code ANALYZING} and the generated requirements
     * and use cases are returned.
     *
     * @param projectId the Project identifier
     * @param userId    the forwarded authenticated user ID
     * @param role      the forwarded authenticated user role
     * @return the analysis result containing requirements and use cases
     */
    @PostMapping("/{projectId}/analyze")
    public ResponseEntity<AnalysisResponse> analyze(
            @PathVariable UUID projectId,
            @RequestHeader(name = USER_ID_HEADER, required = false) String userId,
            @RequestHeader(name = USER_ROLE_HEADER, required = false) String role) {
        ProjectPrincipal principal = ProjectPrincipal.from(userId, role);

        // Require REQUIREMENT_ANALYSIS_START permission (Requirements 4.1, 2.3).
        if (!principal.canStartAnalysis()) {
            return ResponseEntity.status(org.springframework.http.HttpStatus.FORBIDDEN).build();
        }

        Project analyzed = analysisService.analyzeProject(projectId, principal);
        return ResponseEntity.ok(AnalysisResponse.from(analyzed));
    }
}
