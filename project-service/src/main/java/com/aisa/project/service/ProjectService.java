package com.aisa.project.service;

import com.aisa.project.domain.Idea;
import com.aisa.project.domain.Project;
import com.aisa.project.repository.ProjectRepository;
import com.aisa.project.security.ProjectPrincipal;
import com.aisa.project.web.dto.CreateProjectRequest;
import com.aisa.project.web.dto.UpdateProjectRequest;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Application service for the Project aggregate: creation, update, authorized
 * listing, and ownership-scoped retrieval.
 *
 * <p>Structural validation of name (1–200) and description (1–5000) happens at
 * the web boundary via Bean Validation (Requirement 3.11). This service enforces
 * the lifecycle and access rules: a new Project starts in {@code DRAFT} with the
 * creating User as owner (Requirements 3.1, 3.2); listing returns only the
 * Projects the requesting User is authorized to view (Requirement 3.6); and a
 * Project that is absent or not viewable by the requester yields a not-found
 * result (Requirement 3.7).
 *
 * <p>State-machine transitions and requirement analysis are intentionally out of
 * scope here; they are delivered by later tasks.
 */
@Service
public class ProjectService {

    private final ProjectRepository projectRepository;

    public ProjectService(ProjectRepository projectRepository) {
        this.projectRepository = projectRepository;
    }

    /**
     * Creates a Project in the {@code DRAFT} state owned by the requesting User and
     * associates the submitted Idea (name + description) with it
     * (Requirements 3.1, 3.2).
     *
     * @param request   the validated creation request
     * @param principal the authenticated principal (becomes the owner)
     * @return the persisted Project
     */
    @Transactional
    public Project create(CreateProjectRequest request, ProjectPrincipal principal) {
        Project project = new Project(request.name(), request.description(), principal.userId());
        // The submitted name/description constitute the Project's Idea (Requirement 3.1).
        project.setIdea(new Idea(request.name(), request.description()));
        return projectRepository.save(project);
    }

    /**
     * Persists an updated name and description for an existing Project
     * (Requirement 3.3). The Project must be one the requester is authorized to
     * access, otherwise a not-found result is produced (Requirements 3.6, 3.7).
     *
     * @param projectId the Project to update
     * @param request   the validated update request
     * @param principal the authenticated principal
     * @return the updated Project
     * @throws ProjectNotFoundException if the Project is absent or not accessible
     */
    @Transactional
    public Project update(UUID projectId, UpdateProjectRequest request, ProjectPrincipal principal) {
        Project project = requireAccessibleProject(projectId, principal);
        project.setName(request.name());
        project.setDescription(request.description());
        return projectRepository.save(project);
    }

    /**
     * Returns the Projects the requesting User is authorized to view
     * (Requirement 3.6): every Project for a platform-wide reviewer role, or only
     * the User's owned Projects otherwise.
     *
     * @param principal the authenticated principal
     * @return the authorized Projects, newest first
     */
    @Transactional(readOnly = true)
    public List<Project> listAuthorized(ProjectPrincipal principal) {
        if (principal.canViewAllProjects()) {
            return projectRepository.findAll();
        }
        return projectRepository.findByOwnerIdOrderByCreatedAtDesc(principal.userId());
    }

    /**
     * Retrieves a single Project the requester is authorized to view, or raises a
     * not-found result when it is absent or not accessible (Requirements 3.6, 3.7).
     *
     * @param projectId the Project identifier
     * @param principal the authenticated principal
     * @return the Project
     * @throws ProjectNotFoundException if the Project is absent or not accessible
     */
    @Transactional(readOnly = true)
    public Project getById(UUID projectId, ProjectPrincipal principal) {
        return requireAccessibleProject(projectId, principal);
    }

    private Project requireAccessibleProject(UUID projectId, ProjectPrincipal principal) {
        if (principal.canViewAllProjects()) {
            return projectRepository.findById(projectId)
                    .orElseThrow(() -> new ProjectNotFoundException(projectId));
        }
        return projectRepository.findByIdAndOwnerId(projectId, principal.userId())
                .orElseThrow(() -> new ProjectNotFoundException(projectId));
    }
}
