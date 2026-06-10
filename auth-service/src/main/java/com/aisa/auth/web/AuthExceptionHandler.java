package com.aisa.auth.web;

import com.aisa.auth.service.DuplicateAccountException;
import com.aisa.auth.service.InvalidCredentialsException;
import com.aisa.auth.service.InvalidRefreshTokenException;
import com.aisa.commons.correlation.CorrelationContext;
import com.aisa.commons.error.ApiError;
import com.aisa.commons.error.ErrorCodes;
import jakarta.servlet.http.HttpServletRequest;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * Translates registration failures into the shared, client-safe {@link ApiError}
 * contract. Per Requirement 25.7 no internal details are exposed; per
 * Requirement 1.12 validation failures carry per-field messages identifying
 * which requirement was not met.
 */
@RestControllerAdvice
public class AuthExceptionHandler {

    /** Field-level validation failures (Requirement 1.12). */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiError> handleValidation(
            MethodArgumentNotValidException ex, HttpServletRequest request) {
        List<ApiError.FieldError> fieldErrors = ex.getBindingResult().getFieldErrors().stream()
                .map(fe -> new ApiError.FieldError(fe.getField(), fe.getDefaultMessage()))
                .toList();

        ApiError error = ApiError.of(
                ErrorCodes.VALIDATION_ERROR,
                "Registration validation failed",
                fieldErrors,
                correlationId(request));
        return ResponseEntity.badRequest().body(error);
    }

    /** Duplicate-account rejection (Requirement 1.2). */
    @ExceptionHandler(DuplicateAccountException.class)
    public ResponseEntity<ApiError> handleDuplicate(
            DuplicateAccountException ex, HttpServletRequest request) {
        ApiError error = ApiError.of(
                ErrorCodes.DUPLICATE_ACCOUNT,
                ex.getMessage(),
                correlationId(request));
        return ResponseEntity.status(HttpStatus.CONFLICT).body(error);
    }

    /**
     * Failed login (Requirement 1.9). The uniform message carried by the exception does
     * not indicate whether the email or the password was incorrect.
     */
    @ExceptionHandler(InvalidCredentialsException.class)
    public ResponseEntity<ApiError> handleInvalidCredentials(
            InvalidCredentialsException ex, HttpServletRequest request) {
        ApiError error = ApiError.of(
                ErrorCodes.AUTHENTICATION_FAILED,
                ex.getMessage(),
                correlationId(request));
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(error);
    }

    /** Expired, used, revoked, or unrecognized refresh token (Requirement 1.7). */
    @ExceptionHandler(InvalidRefreshTokenException.class)
    public ResponseEntity<ApiError> handleInvalidRefreshToken(
            InvalidRefreshTokenException ex, HttpServletRequest request) {
        ApiError error = ApiError.of(
                ErrorCodes.AUTHENTICATION_FAILED,
                ex.getMessage(),
                correlationId(request));
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(error);
    }

    private static String correlationId(HttpServletRequest request) {
        String fromContext = CorrelationContext.get();
        if (fromContext != null && !fromContext.isBlank()) {
            return fromContext;
        }
        return request.getHeader(CorrelationContext.CORRELATION_ID_HEADER);
    }
}
