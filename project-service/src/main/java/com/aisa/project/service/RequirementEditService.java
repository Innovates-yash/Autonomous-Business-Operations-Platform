package com.aisa.project.service;

import com.aisa.commons.domain.ProjectState;
import com.aisa.project.domain.Project;
import com.aisa.project.domain.Requirement;
import com.aisa.project.domain.RequirementType;
import com.aisa.project.repository.ProjectRepository;
import com.aisa.project.repository.RequirementRepository;
import com.aisa.project.security.ProjectPrincipal;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Service for manual requirement edits while a Project is in the ANALYZING state
 * (Requirement 4.8). Allows users with edit permission to add, modify, or remove
 * generated requirements before confirming the analysis.
 */
@Service
public class RequirementEditService {

    private final ProjectRepository projectRepository;
    private final RequirementRepository requirementRepository;

    public RequirementEditService(ProjectRepository projectRepository,
                                  RequirementRepository requirementRepository) {
        this.projectRepository = projectRepository;
        this.requirementRepository = requirementRepository;
    }

    /**
     * Adds a new requirement to a Project in ANALYZING state (Requirement 4.8).
     *
     * @param project   the project (must be in ANALYZING state)
     * @param statement the requirement statement
     * @param type      the requirement type (FUNCTIONAL or NON_FUNCTIONAL)
     * @return the created requirement
     * @throws InvalidStateTransitionException if project is not in ANALYZING state
     */
    @Transactional
    public Requirement addRequirement(Project project, String statement, RequirementType type) {
        requireAnalyzingState(project);

        Requirement requirement = new Requirement(project, statement, type);
        project.getRequirements().add(requirement);
        projectRepository.save(project);
        return requirement;
    }

    /**
     * Modifies an existing requirement on a Project in ANALYZING state (Requirement 4.8).
     *
     * @param project      the project (must be in ANALYZING state)
     * @param requirementId the requirement to modify
     * @param statement    the new statement (or null to keep current)
     * @param type         the new type (or null to keep current)
     * @return the updated requirement
     * @throws InvalidStateTransitionException if project is not in ANALYZING state
     * @throws RequirementNotFoundException   if the requirement is not found on this project
     */
    @Transactional
    public Requirement modifyRequirement(Project project, UUID requirementId, String statement, RequirementType type) {
        requireAnalyzingState(project);

        Requirement requirement = requirementRepository.findByIdAndProjectId(requirementId, project.getId())
                .orElseThrow(() -> new RequirementNotFoundException(requirementId));

        if (statement != null && !statement.isBlank()) {
            requirement.setStatement(statement);
        }
        if (type != null) {
            requirement.setType(type);
        }

        return requirementRepository.save(requirement);
    }

    /**
     * Removes a requirement from a Project in ANALYZING state (Requirement 4.8).
     *
     * @param project      the project (must be in ANALYZING state)
     * @param requirementId the requirement to remove
     * @throws InvalidStateTransitionException if project is not in ANALYZING state
     * @throws RequirementNotFoundException   if the requirement is not found on this project
     */
    @Transactional
    public void removeRequirement(Project project, UUID requirementId) {
        requireAnalyzingState(project);

        Requirement requirement = requirementRepository.findByIdAndProjectId(requirementId, project.getId())
                .orElseThrow(() -> new RequirementNotFoundException(requirementId));

        project.getRequirements().remove(requirement);
        requirementRepository.delete(requirement);
    }

    private void requireAnalyzingState(Project project) {
        if (project.getState() != ProjectState.ANALYZING) {
            throw new InvalidStateTransitionException(
                    project.getState(), ProjectState.ANALYZING);
        }
    }
}
