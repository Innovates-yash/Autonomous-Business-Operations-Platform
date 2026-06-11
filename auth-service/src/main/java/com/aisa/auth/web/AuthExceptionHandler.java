package com.aisa.auth.web;

import com.aisa.auth.service.AccountLockedException;
import com.aisa.auth.service.AdminOnlyOperationException;
import com.aisa.auth.service.DuplicateAccountException;
import com.aisa.auth.service.InvalidCredentialsException;
import com.aisa.auth.service.InvalidRefreshTokenException;
import com.aisa.auth.service.OAuth2ExchangeException;
import com.aisa.auth.service.RoleNotFoundException;
import com.aisa.auth.service.UserNotFoundException;
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

    /** Account temporarily locked after repeated failed attempts (Requirements 1.11, 1.14). */
    @ExceptionHandler(AccountLockedException.class)
    public ResponseEntity<ApiError> handleAccountLocked(
            AccountLockedException ex, HttpServletRequest request) {
        ApiError error = ApiError.of(
                ErrorCodes.ACCOUNT_LOCKED,
                ex.getMessage(),
                correlationId(request));
        return ResponseEntity.status(HttpStatus.FORBIDDEN).body(error);
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

    /**
     * OAuth2 authorization-code exchange failure or denial (Requirement 1.13). No
     * Platform tokens are issued; the client receives an authentication error.
     */
    @ExceptionHandler(OAuth2ExchangeException.class)
    public ResponseEntity<ApiError> handleOAuth2ExchangeFailure(
            OAuth2ExchangeException ex, HttpServletRequest request) {
        ApiError error = ApiError.of(
                ErrorCodes.AUTHENTICATION_FAILED,
                "OAuth2 authentication failed",
                correlationId(request));
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(error);
    }

    /** Non-Admin attempts an Admin-only operation (Requirement 2.7). */
    @ExceptionHandler(AdminOnlyOperationException.class)
    public ResponseEntity<ApiError> handleAdminOnly(
            AdminOnlyOperationException ex, HttpServletRequest request) {
        ApiError error = ApiError.of(
                ErrorCodes.AUTHORIZATION_DENIED,
                ex.getMessage(),
                correlationId(request));
        return ResponseEntity.status(HttpStatus.FORBIDDEN).body(error);
    }

    /** Target user not found during role assignment. */
    @ExceptionHandler(UserNotFoundException.class)
    public ResponseEntity<ApiError> handleUserNotFound(
            UserNotFoundException ex, HttpServletRequest request) {
        ApiError error = ApiError.of(
                ErrorCodes.NOT_FOUND,
                ex.getMessage(),
                correlationId(request));
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(error);
    }

    /** Requested role does not exist. */
    @ExceptionHandler(RoleNotFoundException.class)
    public ResponseEntity<ApiError> handleRoleNotFound(
            RoleNotFoundException ex, HttpServletRequest request) {
        ApiError error = ApiError.of(
                ErrorCodes.NOT_FOUND,
                ex.getMessage(),
                correlationId(request));
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(error);
    }

    /** Invalid role name that cannot be parsed to a RoleName enum. */
    @ExceptionHandler(InvalidRoleNameException.class)
    public ResponseEntity<ApiError> handleInvalidRoleName(
            InvalidRoleNameException ex, HttpServletRequest request) {
        ApiError error = ApiError.of(
                ErrorCodes.VALIDATION_ERROR,
                ex.getMessage(),
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
