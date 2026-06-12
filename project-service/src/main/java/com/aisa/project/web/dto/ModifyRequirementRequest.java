package com.aisa.project.web.dto;

import com.aisa.project.domain.RequirementType;
import jakarta.validation.constraints.Size;

/**
 * Request payload for modifying an existing requirement while the Project is in
 * ANALYZING state (Requirement 4.8). Both fields are optional; only non-null values
 * are applied.
 *
 * @param statement the new requirement statement (optional, 1–2000 characters)
 * @param type      the new requirement classification (optional)
 */
public record ModifyRequirementRequest(
        @Size(max = 2000, message = "Statement must be at most 2000 characters")
        String statement,

        RequirementType type
) {
}
