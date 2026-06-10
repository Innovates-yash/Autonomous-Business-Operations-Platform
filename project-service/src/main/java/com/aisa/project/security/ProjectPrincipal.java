package com.aisa.project.security;

import com.aisa.commons.domain.Role;
import java.util.UUID;

/**
 * The authenticated principal as forwarded by the API Gateway in the
 * {@code X-User-Id} and {@code X-User-Role} headers. Authentication and JWT
 * validation are performed upstream at the Gateway (Requirement 2); this service
 * trusts the forwarded identity and uses it for ownership and view scoping
 * (Requirement 3.6).
 *
 * @param userId the authenticated User's identifier (owner on create, Requirement 3.2)
 * @param role   the authenticated User's role; {@link Role#GUEST} when unspecified
 */
public record ProjectPrincipal(UUID userId, Role role) {

    /**
     * Builds a principal from the raw forwarded headers.
     *
     * @param userIdHeader the {@code X-User-Id} header value (a UUID)
     * @param roleHeader   the {@code X-User-Role} header value (a {@link Role} name)
     * @return the parsed principal
     * @throws MissingPrincipalException if the user-id header is absent or not a valid UUID
     */
    public static ProjectPrincipal from(String userIdHeader, String roleHeader) {
        if (userIdHeader == null || userIdHeader.isBlank()) {
            throw new MissingPrincipalException("Authenticated principal is required");
        }
        UUID userId;
        try {
            userId = UUID.fromString(userIdHeader.trim());
        } catch (IllegalArgumentException ex) {
            throw new MissingPrincipalException("Authenticated principal identifier is malformed");
        }
        return new ProjectPrincipal(userId, parseRole(roleHeader));
    }

    private static Role parseRole(String roleHeader) {
        if (roleHeader == null || roleHeader.isBlank()) {
            return Role.GUEST;
        }
        try {
            return Role.valueOf(roleHeader.trim().toUpperCase(java.util.Locale.ROOT));
        } catch (IllegalArgumentException ex) {
            return Role.GUEST;
        }
    }

    /**
     * @return true if this principal's role may view every Project regardless of
     *         ownership. ADMIN administers the platform and ARCHITECT reviews and
     *         approves Blueprints, so both hold a platform-wide view (Requirement 3.6).
     */
    public boolean canViewAllProjects() {
        return role == Role.ADMIN || role == Role.ARCHITECT;
    }
}
