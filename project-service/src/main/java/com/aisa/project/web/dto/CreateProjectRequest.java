package com.aisa.project.web.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Project-creation payload (Requirement 3.1). The submitted {@code name} and
 * {@code description} constitute the Project's Idea. Field-level constraints
 * enforce the length rules and produce per-field validation errors when a
 * requirement is not met (Requirement 3.11):
 *
 * <ul>
 *   <li>name: present, 1–200 characters</li>
 *   <li>description: present, 1–5000 characters</li>
 * </ul>
 *
 * @param name        the Project / Idea name
 * @param description the Project / Idea description
 */
public record CreateProjectRequest(
        @NotBlank(message = "Name is required")
        @Size(min = 1, max = 200, message = "Name must be between 1 and 200 characters")
        String name,

        @NotBlank(message = "Description is required")
        @Size(min = 1, max = 5000, message = "Description must be between 1 and 5000 characters")
        String description) {
}
