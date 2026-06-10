package com.aisa.provider.web;

import com.aisa.commons.correlation.CorrelationContext;
import com.aisa.commons.error.ApiError;
import com.aisa.commons.error.ErrorCodes;
import com.aisa.provider.selection.ProviderNotConfiguredException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.List;

/**
 * Translates provider-admin errors into the uniform {@link ApiError} contract.
 *
 * <p>An attempt to select an unconfigured provider is rejected with HTTP 409 and the stable
 * {@link ErrorCodes#PROVIDER_NOT_CONFIGURED} code; the previously selected provider is retained
 * because the service never persisted the rejected selection (Requirement 20.3).
 */
@RestControllerAdvice
public class ProviderAdminExceptionHandler {

    @ExceptionHandler(ProviderNotConfiguredException.class)
    public ResponseEntity<ApiError> handleNotConfigured(ProviderNotConfiguredException ex) {
        ApiError error = ApiError.of(
                ErrorCodes.PROVIDER_NOT_CONFIGURED,
                ex.getMessage(),
                CorrelationContext.get());
        return ResponseEntity.status(HttpStatus.CONFLICT).body(error);
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiError> handleValidation(MethodArgumentNotValidException ex) {
        List<ApiError.FieldError> fieldErrors = ex.getBindingResult().getFieldErrors().stream()
                .map(fe -> new ApiError.FieldError(fe.getField(),
                        fe.getDefaultMessage() == null ? "invalid" : fe.getDefaultMessage()))
                .toList();
        ApiError error = ApiError.of(
                ErrorCodes.VALIDATION_ERROR,
                "Request validation failed",
                fieldErrors,
                CorrelationContext.get());
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(error);
    }
}
