package com.aisa.audit.query;

import com.aisa.audit.domain.AuditEvent;
import com.aisa.audit.repository.AuditEventRepository;
import com.aisa.audit.security.AuditPrincipal;
import java.util.List;
import java.util.Objects;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

/**
 * Read-only query capability over the append-only audit store (Req 23.4–23.6).
 *
 * <p>Authorization is enforced first: only a caller holding the Admin role may
 * query audit events (Req 23.4). Any other role is denied with an
 * {@link AuthorizationDeniedException} before any event is read (Req 23.6).
 *
 * <p>For an authorized caller, the supplied {@link AuditQueryFilter} is applied
 * by the repository's null-aware {@code search} query, which matches on User
 * identity, action, and an inclusive time range and returns results most-recent
 * first. When nothing matches, the query returns an empty list (Req 23.5).
 *
 * <p>This service is intentionally read-only: it exposes no operation that
 * creates, updates, or deletes an audit event, consistent with the append-only,
 * immutable design (Req 23.7).
 */
@Service
public class AuditQueryService {

    private final AuditEventRepository repository;

    public AuditQueryService(AuditEventRepository repository) {
        this.repository = repository;
    }

    /**
     * Returns the audit events matching the filter, after confirming the caller is
     * an Admin.
     *
     * @param principal the authenticated caller forwarded by the Gateway
     * @param filter    the user / action / time-range criteria (fields optional)
     * @param pageable  paging / limit control applied to the ordered result
     * @return matching events ordered most-recent first; empty when none match (Req 23.5)
     * @throws AuthorizationDeniedException if the caller does not hold the Admin role (Req 23.6)
     */
    public List<AuditEvent> query(AuditPrincipal principal, AuditQueryFilter filter, Pageable pageable) {
        Objects.requireNonNull(principal, "principal");
        Objects.requireNonNull(filter, "filter");
        Objects.requireNonNull(pageable, "pageable");

        if (!principal.isAdmin()) {
            throw new AuthorizationDeniedException(
                    "Querying audit events requires the Admin role");
        }

        return repository.search(filter.userId(), filter.action(), filter.from(), filter.to(), pageable);
    }
}
