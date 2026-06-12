package com.aisa.project.web;

import com.aisa.commons.domain.ProjectState;
import com.aisa.project.domain.ClarifyingQuestion;
import com.aisa.project.domain.Project;
import com.aisa.project.domain.Requirement;
import com.aisa.project.security.ProjectPrincipal;
import com.aisa.project.service.ClarifyingQuestionService;
import com.aisa.project.service.ProjectService;
import com.aisa.project.service.ProjectStateMachineService;
import com.aisa.project.service.RequirementEditService;
import com.aisa.project.web.dto.AddRequirementRequest;
import com.aisa.project.web.dto.AnswerQuestionsRequest;
import com.aisa.project.web.dto.ClarifyingQuestionResponse;
import com.aisa.project.web.dto.ModifyRequirementRequest;
import com.aisa.project.web.dto.RequirementResponse;
import com.aisa.project.web.dto.TransitionResponse;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * HTTP endpoints for managing requirements and clarifying questions during the
 * ANALYZING phase (Requirements 4.3, 4.4, 4.7, 4.8).
 *
 * <p>Provides:
 * <ul>
 *   <li>GET questions — retrieves clarifying questions for a project</li>
 *   <li>POST answers — incorporates answers and regenerates affected requirements</li>
 *   <li>POST requirement — adds a requirement manually</li>
 *   <li>PUT requirement — modifies an existing requirement</li>
 *   <li>DELETE requirement — removes a requirement</li>
 *   <li>POST confirm — transitions project from ANALYZING to GENERATING</li>
 * </ul>
 */
@RestController
@RequestMapping("/api/projects")
public class RequirementController {

    static final String USER_ID_HEADER = "X-User-Id";
    static final String USER_ROLE_HEADER = "X-User-Role";

    private final ProjectService projectService;
    private final ClarifyingQuestionService clarifyingQuestionService;
    private final RequirementEditService requirementEditService;
    private final ProjectStateMachineService stateMachineService;

    public RequirementController(ProjectService projectService,
                                 ClarifyingQuestionService clarifyingQuestionService,
                                 RequirementEditService requirementEditService,
                                 ProjectStateMachineService stateMachineService) {
        this.projectService = projectService;
        this.clarifyingQuestionService = clarifyingQuestionService;
        this.requirementEditService = requirementEditService;
        this.stateMachineService = stateMachineService;
    }

    /**
     * Retrieves the clarifying questions for a project (Requirement 4.3).
     */
    @GetMapping("/{projectId}/questions")
    public ResponseEntity<List<ClarifyingQuestionResponse>> getQuestions(
            @PathVariable UUID projectId,
            @RequestHeader(name = USER_ID_HEADER, required = false) String userId,
            @RequestHeader(name = USER_ROLE_HEADER, required = false) String role) {
        ProjectPrincipal principal = ProjectPrincipal.from(userId, role);
        // Verify access to the project.
        projectService.getById(projectId, principal);

        List<ClarifyingQuestionResponse> questions = clarifyingQuestionService.getQuestions(projectId).stream()
                .map(ClarifyingQuestionResponse::from)
                .toList();
        return ResponseEntity.ok(questions);
    }

    /**
     * Submits answers to clarifying questions and regenerates affected requirements
     * (Requirement 4.4).
     */
    @PostMapping("/{projectId}/questions/answers")
    public ResponseEntity<List<RequirementResponse>> answerQuestions(
            @PathVariable UUID projectId,
            @Valid @RequestBody AnswerQuestionsRequest request,
            @RequestHeader(name = USER_ID_HEADER, required = false) String userId,
            @RequestHeader(name = USER_ROLE_HEADER, required = false) String role) {
        ProjectPrincipal principal = ProjectPrincipal.from(userId, role);
        Project project = projectService.getById(projectId, principal);

        Project updated = clarifyingQuestionService.answerQuestions(project, request.answers(), principal);

        List<RequirementResponse> requirements = updated.getRequirements().stream()
                .map(RequirementResponse::from)
                .toList();
        return ResponseEntity.ok(requirements);
    }

    /**
     * Adds a new requirement to a project in ANALYZING state (Requirement 4.8).
     */
    @PostMapping("/{projectId}/requirements")
    public ResponseEntity<RequirementResponse> addRequirement(
            @PathVariable UUID projectId,
            @Valid @RequestBody AddRequirementRequest request,
            @RequestHeader(name = USER_ID_HEADER, required = false) String userId,
            @RequestHeader(name = USER_ROLE_HEADER, required = false) String role) {
        ProjectPrincipal principal = ProjectPrincipal.from(userId, role);
        Project project = projectService.getById(projectId, principal);

        Requirement created = requirementEditService.addRequirement(project, request.statement(), request.type());
        return ResponseEntity.status(HttpStatus.CREATED).body(RequirementResponse.from(created));
    }

    /**
     * Modifies an existing requirement on a project in ANALYZING state (Requirement 4.8).
     */
    @PutMapping("/{projectId}/requirements/{requirementId}")
    public ResponseEntity<RequirementResponse> modifyRequirement(
            @PathVariable UUID projectId,
            @PathVariable UUID requirementId,
            @Valid @RequestBody ModifyRequirementRequest request,
            @RequestHeader(name = USER_ID_HEADER, required = false) String userId,
            @RequestHeader(name = USER_ROLE_HEADER, required = false) String role) {
        ProjectPrincipal principal = ProjectPrincipal.from(userId, role);
        Project project = projectService.getById(projectId, principal);

        Requirement modified = requirementEditService.modifyRequirement(
                project, requirementId, request.statement(), request.type());
        return ResponseEntity.ok(RequirementResponse.from(modified));
    }

    /**
     * Removes a requirement from a project in ANALYZING state (Requirement 4.8).
     */
    @DeleteMapping("/{projectId}/requirements/{requirementId}")
    public ResponseEntity<Void> removeRequirement(
            @PathVariable UUID projectId,
            @PathVariable UUID requirementId,
            @RequestHeader(name = USER_ID_HEADER, required = false) String userId,
            @RequestHeader(name = USER_ROLE_HEADER, required = false) String role) {
        ProjectPrincipal principal = ProjectPrincipal.from(userId, role);
        Project project = projectService.getById(projectId, principal);

        requirementEditService.removeRequirement(project, requirementId);
        return ResponseEntity.noContent().build();
    }

    /**
     * Confirms the requirement analysis, transitioning the project from ANALYZING
     * to GENERATING (Requirement 4.7).
     */
    @PostMapping("/{projectId}/confirm-analysis")
    public ResponseEntity<TransitionResponse> confirmAnalysis(
            @PathVariable UUID projectId,
            @RequestHeader(name = USER_ID_HEADER, required = false) String userId,
            @RequestHeader(name = USER_ROLE_HEADER, required = false) String role) {
        ProjectPrincipal principal = ProjectPrincipal.from(userId, role);
        Project project = projectService.getById(projectId, principal);

        ProjectState fromState = project.getState();
        Project confirmed = stateMachineService.transition(projectId, ProjectState.GENERATING, principal);
        return ResponseEntity.ok(TransitionResponse.from(confirmed, fromState));
    }
}
