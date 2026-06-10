package com.aisa.project.web.dto;

import com.aisa.commons.domain.ProjectState;
import com.aisa.project.domain.Project;
import java.time.Instant;
import java.util.UUID;

/**
 * Client-safe view of a {@link Project}. Carries the Project's identity,
 * name/description, lifecycle state, owner, and audit timestamps.
 *
 * @param id          the Project identifier
 * @param name        the Project name
 * @param description the Project description
 * @param state       the current lifecycle state (Requirement 3.4)
 * @param ownerId     identifier of the owning User (Requirement 3.2)
 * @param createdAt   creation timestamp
 * @param updatedAt   last-update timestamp
 */
public record ProjectResponse(
        UUID id,
        String name,
        String description,
        ProjectState state,
        UUID ownerId,
        Instant createdAt,
        Instant updatedAt) {

    public static ProjectResponse from(Project project) {
        return new ProjectResponse(
                project.getId(),
                project.getName(),
                project.getDescription(),
                project.getState(),
                project.getOwnerId(),
                project.getCreatedAt(),
                project.getUpdatedAt());
    }
}
