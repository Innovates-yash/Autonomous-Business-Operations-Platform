package com.aisa.project.web.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Project-update payload (Requirement 3.3). Updating persists the new
 * {@code name} (1–200 characters) and {@code description} (1–5000 characters).
 * Values outside those bounds are rejected with per-field validation errors and
 * no change to stored Project data (Requirement 3.11).
 *
 * @param name        the new Project name
 * @param description the new Project description
 */
public record UpdateProjectRequest(
        @NotBlank(message = "Name is required")
        @Size(min = 1, max = 200, message = "Name must be between 1 and 200 characters")
        String name,

        @NotBlank(message = "Description is required")
        @Size(min = 1, max = 5000, message = "Description must be between 1 and 5000 characters")
        String description) {
}
