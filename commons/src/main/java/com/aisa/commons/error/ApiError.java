package com.aisa.commons.error;

import java.time.Instant;
import java.util.List;

/**
 * Uniform, client-safe error payload returned by all services.
 *
 * <p>Per Requirement 25.7, error responses must not expose internal implementation
 * details (stack traces, file paths, database structure). This record carries only a
 * stable error code, a human-readable message, optional field-level validation details,
 * the correlation identifier, and a timestamp.
 *
 * @param code        stable, machine-readable error code (e.g. {@code VALIDATION_ERROR})
 * @param message     client-safe human-readable description
 * @param fieldErrors per-field validation messages, may be empty
 * @param correlationId correlation identifier for support/tracing
 * @param timestamp   time the error was produced
 */
public record ApiError(
        String code,
        String message,
        List<FieldError> fieldErrors,
        String correlationId,
        Instant timestamp
) {
    public record FieldError(String field, String message) {
    }

    public static ApiError of(String code, String message, String correlationId) {
        return new ApiError(code, message, List.of(), correlationId, Instant.now());
    }

    public static ApiError of(String code, String message, List<FieldError> fieldErrors, String correlationId) {
        return new ApiError(code, message, fieldErrors, correlationId, Instant.now());
    }
}
