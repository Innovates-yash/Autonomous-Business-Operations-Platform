package com.aisa.project.service;

import java.util.UUID;

/**
 * Raised when a requested Project does not exist, or exists but the requesting
 * User is not authorized to view it. Both cases map to a not-found result
 * (Requirements 3.6, 3.7) so that the existence of Projects owned by other Users
 * is not disclosed.
 */
public class ProjectNotFoundException extends RuntimeException {

    public ProjectNotFoundException(UUID projectId) {
        super("Project not found: " + projectId);
    }
}
