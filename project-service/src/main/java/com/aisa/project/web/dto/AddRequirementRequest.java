package com.aisa.project.web.dto;

import com.aisa.project.domain.RequirementType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * Request payload for adding a requirement manually while the Project is in
 * ANALYZING state (Requirement 4.8).
 *
 * @param statement the requirement statement (1–2000 characters)
 * @param type      the requirement classification (FUNCTIONAL or NON_FUNCTIONAL)
 */
public record AddRequirementRequest(
        @NotBlank(message = "Requirement statement is required")
        @Size(max = 2000, message = "Statement must be at most 2000 characters")
        String statement,

        @NotNull(message = "Requirement type is required")
        RequirementType type
) {
}
