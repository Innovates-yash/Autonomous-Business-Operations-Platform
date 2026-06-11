package com.aisa.audit.web;

import com.aisa.audit.domain.AuditAction;
import com.aisa.audit.query.AuditQueryFilter;
import com.aisa.audit.query.AuditQueryService;
import com.aisa.audit.security.AuditPrincipal;
import com.aisa.audit.web.dto.AuditEventResponse;
import java.time.Instant;
import java.util.List;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Read-only HTTP API for the Admin-only audit query (Req 23.4–23.6).
 *
 * <p>This controller exposes exactly one capability: an authenticated Admin may
 * read audit events filtered by User identity, action, and time range. It
 * intentionally declares <em>no</em> create, update, or delete mapping — the
 * audit store is append-only and immutable (Req 23.7). Inserts happen only via
 * the Kafka recording pipeline, never over this API; any attempt to modify or
 * delete an audit record through HTTP is rejected by {@link AuditExceptionHandler}.
 *
 * <p>Authentication and JWT validation occur upstream at the API Gateway, which
 * forwards the authenticated principal in the {@code X-User-Id} and
 * {@code X-User-Role} headers. Admin authorization is enforced here via
 * {@code X-User-Role}: non-Admin callers are denied with the shared
 * {@link com.aisa.commons.error.ApiError} contract (Req 23.6).
 */
@RestController
@RequestMapping("/api/audit/events")
public class AuditQueryController {

    static final String USER_ID_HEADER = "X-User-Id";
    static final String USER_ROLE_HEADER = "X-User-Role";

    /** Default page size when the caller does not specify one. */
    static final int DEFAULT_PAGE_SIZE = 50;
    /** Upper bound on page size to protect the service from unbounded reads. */
    static final int MAX_PAGE_SIZE = 500;

    private final AuditQueryService queryService;

    public AuditQueryController(AuditQueryService queryService) {
        this.queryService = queryService;
    }

    /**
     * Returns audit events matching the supplied filters, most-recent first
     * (Req 23.4). Returns an empty list when nothing matches (Req 23.5). Non-Admin
     * callers are denied (Req 23.6).
     *
     * @param userId   forwarded caller identity ({@code X-User-Id})
     * @param role     forwarded caller role ({@code X-User-Role}); must be Admin
     * @param filterUserId optional User-identity filter
     * @param action   optional action filter
     * @param from     optional inclusive lower bound on the event timestamp (ISO-8601)
     * @param to       optional inclusive upper bound on the event timestamp (ISO-8601)
     * @param page     zero-based page index (defaults to 0)
     * @param size     page size (defaults to {@value #DEFAULT_PAGE_SIZE}, capped at {@value #MAX_PAGE_SIZE})
     */
    @GetMapping
    public ResponseEntity<List<AuditEventResponse>> query(
            @RequestHeader(name = USER_ID_HEADER, required = false) String userId,
            @RequestHeader(name = USER_ROLE_HEADER, required = false) String role,
            @RequestParam(name = "userId", required = false) String filterUserId,
            @RequestParam(name = "action", required = false) AuditAction action,
            @RequestParam(name = "from", required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant from,
            @RequestParam(name = "to", required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant to,
            @RequestParam(name = "page", required = false, defaultValue = "0") int page,
            @RequestParam(name = "size", required = false, defaultValue = "50") int size) {

        AuditPrincipal principal = AuditPrincipal.from(userId, role);
        AuditQueryFilter filter = new AuditQueryFilter(blankToNull(filterUserId), action, from, to);
        Pageable pageable = PageRequest.of(Math.max(page, 0), boundedSize(size));

        List<AuditEventResponse> events = queryService.query(principal, filter, pageable).stream()
                .map(AuditEventResponse::from)
                .toList();
        return ResponseEntity.ok(events);
    }

    private static String blankToNull(String value) {
        return (value == null || value.isBlank()) ? null : value.trim();
    }

    private static int boundedSize(int requested) {
        if (requested <= 0) {
            return DEFAULT_PAGE_SIZE;
        }
        return Math.min(requested, MAX_PAGE_SIZE);
    }
}
