package com.aisa.project.service;

import java.util.UUID;

/**
 * Thrown when a Requirement is not found within the expected Project scope.
 */
public class RequirementNotFoundException extends RuntimeException {

    private final UUID requirementId;

    public RequirementNotFoundException(UUID requirementId) {
        super("Requirement not found: " + requirementId);
        this.requirementId = requirementId;
    }

    public UUID getRequirementId() {
        return requirementId;
    }
}
