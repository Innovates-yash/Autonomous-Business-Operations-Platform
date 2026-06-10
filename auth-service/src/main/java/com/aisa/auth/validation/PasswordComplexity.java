package com.aisa.auth.validation;

import jakarta.validation.Constraint;
import jakarta.validation.Payload;
import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Validates that a password contains at least one uppercase letter, one
 * lowercase letter, one digit, and one special (non-alphanumeric) character
 * (Requirement 1.1 / 1.12).
 *
 * <p>This constraint checks character-class composition only. Length bounds
 * (12–128 characters) and presence are enforced separately by {@code @Size}
 * and {@code @NotBlank} so each failed rule surfaces as its own field-level
 * validation message.
 */
@Documented
@Constraint(validatedBy = PasswordComplexityValidator.class)
@Target({ElementType.FIELD, ElementType.PARAMETER})
@Retention(RetentionPolicy.RUNTIME)
public @interface PasswordComplexity {

    String message() default "Password does not meet complexity requirements";

    Class<?>[] groups() default {};

    Class<? extends Payload>[] payload() default {};
}
