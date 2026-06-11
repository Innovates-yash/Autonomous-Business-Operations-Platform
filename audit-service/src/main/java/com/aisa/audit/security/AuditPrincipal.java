package com.aisa.audit.security;

import com.aisa.commons.domain.Role;
import java.util.Locale;

/**
 * The authenticated principal as forwarded by the API Gateway in the
 * {@code X-User-Id} and {@code X-User-Role} headers. Authentication and JWT
 * validation are performed upstream at the Gateway (Requirement 2); this service
 * trusts the forwarded identity and uses only the role to gate the Admin-only
 * audit query capability (Req 23.4, 23.6).
 *
 * @param userId the authenticated caller's identifier (the querying Admin)
 * @param role   the authenticated caller's role; {@link Role#GUEST} when unspecified
 *               or unrecognized
 */
public record AuditPrincipal(String userId, Role role) {

    /**
     * Builds a principal from the raw forwarded headers.
     *
     * @param userIdHeader the {@code X-User-Id} header value
     * @param roleHeader   the {@code X-User-Role} header value (a {@link Role} name)
     * @return the parsed principal
     * @throws MissingPrincipalException if the user-id header is absent or blank
     */
    public static AuditPrincipal from(String userIdHeader, String roleHeader) {
        if (userIdHeader == null || userIdHeader.isBlank()) {
            throw new MissingPrincipalException("Authenticated principal is required");
        }
        return new AuditPrincipal(userIdHeader.trim(), parseRole(roleHeader));
    }

    private static Role parseRole(String roleHeader) {
        if (roleHeader == null || roleHeader.isBlank()) {
            return Role.GUEST;
        }
        try {
            return Role.valueOf(roleHeader.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException ex) {
            return Role.GUEST;
        }
    }

    /**
     * @return true only when the caller holds the Admin role. Audit queries are
     *         permitted exclusively for Admins (Req 23.4); every other role is
     *         denied (Req 23.6).
     */
    public boolean isAdmin() {
        return role == Role.ADMIN;
    }
}
