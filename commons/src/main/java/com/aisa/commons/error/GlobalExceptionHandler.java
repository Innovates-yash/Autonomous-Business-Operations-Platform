package com.aisa.commons.error;

import com.aisa.commons.correlation.CorrelationContext;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.ConstraintViolationException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import java.util.List;

/**
 * Global exception handler that ensures no internal details leak in error responses.
 *
 * <p>Per Requirement 25.7, error responses must not expose stack traces, class names,
 * SQL, or any other internal implementation detail. This handler catches common exception
 * types and returns safe {@link ApiError} payloads using stable error codes.
 *
 * <p>Per Requirement 25.6, all input is validated (Bean Validation) and rejected with
 * descriptive but safe messages on failure.
 *
 * <p>This handler runs at the lowest precedence so that service-specific
 * {@code @RestControllerAdvice} handlers (e.g., auth, project) take priority for their
 * domain exceptions while this handler catches anything they don't cover.
 */
@RestControllerAdvice
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
@Order(Ordered.LOWEST_PRECEDENCE)
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    // ---- Validation errors (Requirement 25.6) ----

    /**
     * Handles Bean Validation failures on @Valid @RequestBody parameters.
     * Returns per-field error messages.
     */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiError> handleMethodArgumentNotValid(
            MethodArgumentNotValidException ex, HttpServletRequest request) {
        List<ApiError.FieldError> fieldErrors = ex.getBindingResult().getFieldErrors().stream()
                .map(fe -> new ApiError.FieldError(
                        fe.getField(),
                        fe.getDefaultMessage() == null ? "invalid" : fe.getDefaultMessage()))
                .toList();

        ApiError error = ApiError.of(
                ErrorCodes.VALIDATION_ERROR,
                "Request validation failed",
                fieldErrors,
                correlationId(request));
        return ResponseEntity.badRequest().body(error);
    }

    /**
     * Handles Bean Validation constraint violations on path variables and request params.
     */
    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<ApiError> handleConstraintViolation(
            ConstraintViolationException ex, HttpServletRequest request) {
        List<ApiError.FieldError> fieldErrors = ex.getConstraintViolations().stream()
                .map(cv -> {
                    String path = cv.getPropertyPath().toString();
                    // Extract just the parameter name from the path
                    String field = path.contains(".") ? path.substring(path.lastIndexOf('.') + 1) : path;
                    return new ApiError.FieldError(field, cv.getMessage());
                })
                .toList();

        ApiError error = ApiError.of(
                ErrorCodes.VALIDATION_ERROR,
                "Request validation failed",
                fieldErrors,
                correlationId(request));
        return ResponseEntity.badRequest().body(error);
    }

    /**
     * Handles missing required request parameters.
     */
    @ExceptionHandler(MissingServletRequestParameterException.class)
    public ResponseEntity<ApiError> handleMissingParam(
            MissingServletRequestParameterException ex, HttpServletRequest request) {
        List<ApiError.FieldError> fieldErrors = List.of(
                new ApiError.FieldError(ex.getParameterName(), "parameter is required"));

        ApiError error = ApiError.of(
                ErrorCodes.VALIDATION_ERROR,
                "Request validation failed",
                fieldErrors,
                correlationId(request));
        return ResponseEntity.badRequest().body(error);
    }

    /**
     * Handles type conversion failures in path variables and request params.
     */
    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<ApiError> handleTypeMismatch(
            MethodArgumentTypeMismatchException ex, HttpServletRequest request) {
        String field = ex.getName();
        ApiError error = ApiError.of(
                ErrorCodes.VALIDATION_ERROR,
                "Request validation failed",
                List.of(new ApiError.FieldError(field, "invalid value")),
                correlationId(request));
        return ResponseEntity.badRequest().body(error);
    }

    /**
     * Handles malformed JSON or unreadable request bodies.
     */
    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ApiError> handleNotReadable(
            HttpMessageNotReadableException ex, HttpServletRequest request) {
        ApiError error = ApiError.of(
                ErrorCodes.VALIDATION_ERROR,
                "Malformed request body",
                correlationId(request));
        return ResponseEntity.badRequest().body(error);
    }

    // ---- Size limit errors (Requirement 25.6) ----

    /**
     * Handles multipart/form upload size exceeded (1 MB limit).
     */
    @ExceptionHandler(MaxUploadSizeExceededException.class)
    public ResponseEntity<ApiError> handleMaxUploadSize(
            MaxUploadSizeExceededException ex, HttpServletRequest request) {
        ApiError error = ApiError.of(
                ErrorCodes.PAYLOAD_TOO_LARGE,
                "Request payload exceeds the maximum allowed size of 1 MB",
                correlationId(request));
        return ResponseEntity.status(HttpStatus.PAYLOAD_TOO_LARGE).body(error);
    }

    // ---- HTTP method/media type errors ----

    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    public ResponseEntity<ApiError> handleMethodNotSupported(
            HttpRequestMethodNotSupportedException ex, HttpServletRequest request) {
        ApiError error = ApiError.of(
                ErrorCodes.VALIDATION_ERROR,
                "HTTP method not supported",
                correlationId(request));
        return ResponseEntity.status(HttpStatus.METHOD_NOT_ALLOWED).body(error);
    }

    @ExceptionHandler(HttpMediaTypeNotSupportedException.class)
    public ResponseEntity<ApiError> handleMediaTypeNotSupported(
            HttpMediaTypeNotSupportedException ex, HttpServletRequest request) {
        ApiError error = ApiError.of(
                ErrorCodes.VALIDATION_ERROR,
                "Unsupported media type",
                correlationId(request));
        return ResponseEntity.status(HttpStatus.UNSUPPORTED_MEDIA_TYPE).body(error);
    }

    @ExceptionHandler(NoResourceFoundException.class)
    public ResponseEntity<ApiError> handleNoResourceFound(
            NoResourceFoundException ex, HttpServletRequest request) {
        ApiError error = ApiError.of(
                ErrorCodes.NOT_FOUND,
                "Resource not found",
                correlationId(request));
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(error);
    }

    // ---- Catch-all: ensures no internal details leak (Requirement 25.7) ----

    /**
     * Generic fallback handler. Logs the full exception for operators but returns
     * only a safe, generic message to the client — no class names, stack traces,
     * SQL, or file paths.
     */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiError> handleGenericException(
            Exception ex, HttpServletRequest request) {
        log.error("Unhandled exception on {} {}: {}", request.getMethod(),
                request.getRequestURI(), ex.getMessage(), ex);

        ApiError error = ApiError.of(
                ErrorCodes.INTERNAL_ERROR,
                "An unexpected error occurred. Please try again later.",
                correlationId(request));
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(error);
    }

    private static String correlationId(HttpServletRequest request) {
        String fromContext = CorrelationContext.get();
        if (fromContext != null && !fromContext.isBlank()) {
            return fromContext;
        }
        String header = request.getHeader(CorrelationContext.CORRELATION_ID_HEADER);
        return header != null ? header : "unknown";
    }
}
