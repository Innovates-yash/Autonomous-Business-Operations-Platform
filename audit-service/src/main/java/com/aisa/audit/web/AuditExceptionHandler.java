package com.aisa.audit.web;

import com.aisa.audit.query.AuthorizationDeniedException;
import com.aisa.audit.security.MissingPrincipalException;
import com.aisa.commons.correlation.CorrelationContext;
import com.aisa.commons.error.ApiError;
import com.aisa.commons.error.ErrorCodes;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

/**
 * Translates Audit_Service query failures into the shared, client-safe
 * {@link ApiError} contract.
 *
 * <ul>
 *   <li>Non-Admin callers are denied with {@code AUTHORIZATION_DENIED} (Req 23.6).</li>
 *   <li>Any attempt to modify or delete an audit record over HTTP (e.g. a POST,
 *       PUT, PATCH, or DELETE against the audit resource, which this API does not
 *       expose) is rejected with an immutability error (Req 23.7).</li>
 *   <li>Malformed filter values (an unknown action or an unparseable timestamp)
 *       are reported as validation errors.</li>
 * </ul>
 *
 * <p>Per Requirement 25.7 no internal implementation details are exposed.
 */
@RestControllerAdvice
public class AuditExceptionHandler {

    /**
     * Stable, audit-specific error code denoting that audit events may never be
     * modified or deleted (Req 23.7).
     */
    static final String AUDIT_IMMUTABLE = "AUDIT_IMMUTABLE";

    /** Non-Admin caller attempted to query audit events (Req 23.6). */
    @ExceptionHandler(AuthorizationDeniedException.class)
    public ResponseEntity<ApiError> handleAuthorizationDenied(
            AuthorizationDeniedException ex, HttpServletRequest request) {
        ApiError error = ApiError.of(
                ErrorCodes.AUTHORIZATION_DENIED,
                "Querying audit events requires the Admin role",
                correlationId(request));
        return ResponseEntity.status(HttpStatus.FORBIDDEN).body(error);
    }

    /** Missing or malformed forwarded principal (defensive; the Gateway normally supplies it). */
    @ExceptionHandler(MissingPrincipalException.class)
    public ResponseEntity<ApiError> handleMissingPrincipal(
            MissingPrincipalException ex, HttpServletRequest request) {
        ApiError error = ApiError.of(
                ErrorCodes.AUTHORIZATION_DENIED,
                "Authenticated principal is required",
                correlationId(request));
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(error);
    }

    /**
     * Any non-read HTTP method against the audit resource. The API exposes only a
     * read operation; audit events are append-only and immutable, so modification
     * or deletion attempts are rejected and the record is left unchanged (Req 23.7).
     */
    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    public ResponseEntity<ApiError> handleModificationAttempt(
            HttpRequestMethodNotSupportedException ex, HttpServletRequest request) {
        ApiError error = ApiError.of(
                AUDIT_IMMUTABLE,
                "Audit events are immutable: they cannot be modified or deleted",
                correlationId(request));
        return ResponseEntity.status(HttpStatus.METHOD_NOT_ALLOWED).body(error);
    }

    /** Unknown action or unparseable timestamp in the query filters. */
    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<ApiError> handleTypeMismatch(
            MethodArgumentTypeMismatchException ex, HttpServletRequest request) {
        ApiError error = ApiError.of(
                ErrorCodes.VALIDATION_ERROR,
                "Invalid value for query parameter '" + ex.getName() + "'",
                correlationId(request));
        return ResponseEntity.badRequest().body(error);
    }

    private static String correlationId(HttpServletRequest request) {
        String fromContext = CorrelationContext.get();
        if (fromContext != null && !fromContext.isBlank()) {
            return fromContext;
        }
        return request.getHeader(CorrelationContext.CORRELATION_ID_HEADER);
    }
}
