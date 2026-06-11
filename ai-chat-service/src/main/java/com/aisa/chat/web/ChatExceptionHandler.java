package com.aisa.chat.web;

import com.aisa.commons.correlation.CorrelationContext;
import com.aisa.commons.error.ErrorCodes;
import jakarta.servlet.http.HttpServletRequest;
import java.time.Instant;
import java.util.List;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingRequestHeaderException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * Chat-specific exception handler. Overrides the global handler for validation
 * errors to include the user's submitted content in the response, satisfying
 * Requirement 5.2: "preserve the User's submitted message."
 */
@RestControllerAdvice(assignableTypes = ChatController.class)
@Order(Ordered.HIGHEST_PRECEDENCE)
public class ChatExceptionHandler {

    /**
     * Field-level validation failures (Requirement 5.2). Returns the rejected value
     * in the error response so the client can recover and redisplay the user's input.
     */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ChatValidationError> handleValidation(
            MethodArgumentNotValidException ex, HttpServletRequest request) {
        List<ChatValidationError.FieldError> fieldErrors = ex.getBindingResult().getFieldErrors().stream()
                .map(fe -> new ChatValidationError.FieldError(
                        fe.getField(),
                        fe.getDefaultMessage(),
                        fe.getRejectedValue()))
                .toList();

        ChatValidationError error = new ChatValidationError(
                ErrorCodes.VALIDATION_ERROR,
                "Message validation failed",
                fieldErrors,
                correlationId(request),
                Instant.now());
        return ResponseEntity.badRequest().body(error);
    }

    /**
     * Missing X-User-Id header when the gateway doesn't inject the principal.
     */
    @ExceptionHandler(MissingRequestHeaderException.class)
    public ResponseEntity<ChatValidationError> handleMissingHeader(
            MissingRequestHeaderException ex, HttpServletRequest request) {
        ChatValidationError error = new ChatValidationError(
                ErrorCodes.VALIDATION_ERROR,
                "Missing required header: " + ex.getHeaderName(),
                List.of(),
                correlationId(request),
                Instant.now());
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(error);
    }

    private static String correlationId(HttpServletRequest request) {
        String fromContext = CorrelationContext.get();
        if (fromContext != null && !fromContext.isBlank()) {
            return fromContext;
        }
        String header = request.getHeader(CorrelationContext.CORRELATION_ID_HEADER);
        return header != null ? header : "unknown";
    }

    /**
     * Validation error response that preserves rejected values (Requirement 5.2).
     */
    public record ChatValidationError(
            String code,
            String message,
            List<FieldError> fieldErrors,
            String correlationId,
            Instant timestamp
    ) {
        public record FieldError(String field, String message, Object rejectedValue) {
        }
    }
}
