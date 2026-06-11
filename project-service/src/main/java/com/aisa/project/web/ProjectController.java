package com.aisa.project.web;

import com.aisa.commons.domain.ProjectState;
import com.aisa.project.domain.Project;
import com.aisa.project.security.ProjectPrincipal;
import com.aisa.project.service.ProjectService;
import com.aisa.project.service.ProjectStateMachineService;
import com.aisa.project.web.dto.CreateProjectRequest;
import com.aisa.project.web.dto.ProjectResponse;
import com.aisa.project.web.dto.TransitionRequest;
import com.aisa.project.web.dto.TransitionResponse;
import com.aisa.project.web.dto.UpdateProjectRequest;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Project lifecycle HTTP API. Implements CRUD with validation and view scoping
 * (Requirements 3.1, 3.2, 3.3, 3.6, 3.7, 3.11) and state-machine transitions
 * (Requirements 3.4, 3.5, 3.8, 3.9, 3.10).
 *
 * <p>Authentication and JWT validation occur upstream at the API Gateway, which
 * forwards the authenticated principal in the {@code X-User-Id} and
 * {@code X-User-Role} headers. This service trusts that identity for ownership
 * and scoping decisions.
 */
@RestController
@RequestMapping("/api/projects")
public class ProjectController {

    static final String USER_ID_HEADER = "X-User-Id";
    static final String USER_ROLE_HEADER = "X-User-Role";

    private final ProjectService projectService;
    private final ProjectStateMachineService stateMachineService;

    public ProjectController(ProjectService projectService,
                             ProjectStateMachineService stateMachineService) {
        this.projectService = projectService;
        this.stateMachineService = stateMachineService;
    }

    /**
     * Creates a Project in the {@code DRAFT} state owned by the caller
     * (Requirements 3.1, 3.2). Returns {@code 201 Created}. Validation failures are
     * translated to per-field errors by {@link ProjectExceptionHandler}
     * (Requirement 3.11).
     */
    @PostMapping
    public ResponseEntity<ProjectResponse> create(
            @Valid @RequestBody CreateProjectRequest request,
            @RequestHeader(name = USER_ID_HEADER, required = false) String userId,
            @RequestHeader(name = USER_ROLE_HEADER, required = false) String role) {
        ProjectPrincipal principal = ProjectPrincipal.from(userId, role);
        Project created = projectService.create(request, principal);
        return ResponseEntity.status(HttpStatus.CREATED).body(ProjectResponse.from(created));
    }

    /**
     * Updates a Project's name and description (Requirement 3.3). A Project the
     * caller cannot access yields {@code 404 Not Found} (Requirements 3.6, 3.7).
     */
    @PutMapping("/{projectId}")
    public ResponseEntity<ProjectResponse> update(
            @PathVariable UUID projectId,
            @Valid @RequestBody UpdateProjectRequest request,
            @RequestHeader(name = USER_ID_HEADER, required = false) String userId,
            @RequestHeader(name = USER_ROLE_HEADER, required = false) String role) {
        ProjectPrincipal principal = ProjectPrincipal.from(userId, role);
        Project updated = projectService.update(projectId, request, principal);
        return ResponseEntity.ok(ProjectResponse.from(updated));
    }

    /**
     * Lists the Projects the caller is authorized to view (Requirement 3.6).
     */
    @GetMapping
    public ResponseEntity<List<ProjectResponse>> list(
            @RequestHeader(name = USER_ID_HEADER, required = false) String userId,
            @RequestHeader(name = USER_ROLE_HEADER, required = false) String role) {
        ProjectPrincipal principal = ProjectPrincipal.from(userId, role);
        List<ProjectResponse> projects = projectService.listAuthorized(principal).stream()
                .map(ProjectResponse::from)
                .toList();
        return ResponseEntity.ok(projects);
    }

    /**
     * Retrieves a single Project, returning {@code 404 Not Found} when it is absent
     * or not accessible to the caller (Requirements 3.6, 3.7).
     */
    @GetMapping("/{projectId}")
    public ResponseEntity<ProjectResponse> get(
            @PathVariable UUID projectId,
            @RequestHeader(name = USER_ID_HEADER, required = false) String userId,
            @RequestHeader(name = USER_ROLE_HEADER, required = false) String role) {
        ProjectPrincipal principal = ProjectPrincipal.from(userId, role);
        Project project = projectService.getById(projectId, principal);
        return ResponseEntity.ok(ProjectResponse.from(project));
    }

    /**
     * Transitions a Project to the requested target state
     * (Requirements 3.4, 3.5, 3.8, 3.9, 3.10). Returns the updated Project with
     * the from/to states on success. Invalid transitions yield {@code 409 Conflict}
     * with state preserved.
     */
    @PostMapping("/{projectId}/transitions")
    public ResponseEntity<TransitionResponse> transition(
            @PathVariable UUID projectId,
            @Valid @RequestBody TransitionRequest request,
            @RequestHeader(name = USER_ID_HEADER, required = false) String userId,
            @RequestHeader(name = USER_ROLE_HEADER, required = false) String role) {
        ProjectPrincipal principal = ProjectPrincipal.from(userId, role);
        ProjectState fromState = projectService.getById(projectId, principal).getState();
        Project transitioned = stateMachineService.transition(projectId, request.targetState(), principal);
        return ResponseEntity.ok(TransitionResponse.from(transitioned, fromState));
    }
}
