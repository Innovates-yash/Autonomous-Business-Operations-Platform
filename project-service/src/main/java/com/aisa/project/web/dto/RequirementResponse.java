package com.aisa.project.web.dto;

import com.aisa.project.domain.Requirement;
import java.time.Instant;
import java.util.UUID;

/**
 * Response DTO for a single requirement entity.
 */
public record RequirementResponse(
        UUID id,
        String statement,
        String type,
        String recommendedAssumption,
        Instant createdAt
) {

    public static RequirementResponse from(Requirement requirement) {
        return new RequirementResponse(
                requirement.getId(),
                requirement.getStatement(),
                requirement.getType().name(),
                requirement.getRecommendedAssumption(),
                requirement.getCreatedAt()
        );
    }
}
