package com.aisa.project.web;

import com.aisa.commons.correlation.CorrelationContext;
import com.aisa.commons.error.ApiError;
import com.aisa.commons.error.ErrorCodes;
import com.aisa.project.security.MissingPrincipalException;
import com.aisa.project.service.InvalidStateTransitionException;
import com.aisa.project.service.ProjectNotFoundException;
import jakarta.servlet.http.HttpServletRequest;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * Translates Project Service failures into the shared, client-safe {@link ApiError}
 * contract. Per Requirement 25.7 no internal details are exposed; per
 * Requirement 3.11 validation failures carry per-field messages identifying which
 * field failed, and per Requirements 3.6/3.7 an absent or inaccessible Project is
 * reported as not-found.
 */
@RestControllerAdvice(assignableTypes = ProjectController.class)
public class ProjectExceptionHandler {

    /** Field-level validation failures (Requirement 3.11). */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiError> handleValidation(
            MethodArgumentNotValidException ex, HttpServletRequest request) {
        List<ApiError.FieldError> fieldErrors = ex.getBindingResult().getFieldErrors().stream()
                .map(fe -> new ApiError.FieldError(
                        fe.getField(),
                        fe.getDefaultMessage() == null ? "invalid" : fe.getDefaultMessage()))
                .toList();

        ApiError error = ApiError.of(
                ErrorCodes.VALIDATION_ERROR,
                "Project validation failed",
                fieldErrors,
                correlationId(request));
        return ResponseEntity.badRequest().body(error);
    }

    /** Absent or inaccessible Project (Requirements 3.6, 3.7). */
    @ExceptionHandler(ProjectNotFoundException.class)
    public ResponseEntity<ApiError> handleNotFound(
            ProjectNotFoundException ex, HttpServletRequest request) {
        ApiError error = ApiError.of(
                ErrorCodes.NOT_FOUND,
                "Project not found",
                correlationId(request));
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(error);
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

    /** Invalid state transition — current state is preserved (Requirement 3.10). */
    @ExceptionHandler(InvalidStateTransitionException.class)
    public ResponseEntity<ApiError> handleInvalidStateTransition(
            InvalidStateTransitionException ex, HttpServletRequest request) {
        ApiError error = ApiError.of(
                ErrorCodes.INVALID_STATE_TRANSITION,
                ex.getMessage(),
                correlationId(request));
        return ResponseEntity.status(HttpStatus.CONFLICT).body(error);
    }

    private static String correlationId(HttpServletRequest request) {
        String fromContext = CorrelationContext.get();
        if (fromContext != null && !fromContext.isBlank()) {
            return fromContext;
        }
        return request.getHeader(CorrelationContext.CORRELATION_ID_HEADER);
    }
}
