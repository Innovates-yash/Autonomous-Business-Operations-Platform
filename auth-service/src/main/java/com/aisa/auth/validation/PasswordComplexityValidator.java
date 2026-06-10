package com.aisa.auth.validation;

import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;
import java.util.ArrayList;
import java.util.List;

/**
 * Enforces the password character-class rules of {@link PasswordComplexity}.
 *
 * <p>When one or more required classes are missing the validator replaces the
 * default message with one that names exactly which requirements were not met,
 * so the client receives an actionable field-level error (Requirement 1.12).
 */
public class PasswordComplexityValidator
        implements ConstraintValidator<PasswordComplexity, String> {

    @Override
    public boolean isValid(String value, ConstraintValidatorContext context) {
        // Null/blank and length are handled by @NotBlank / @Size respectively.
        if (value == null || value.isEmpty()) {
            return true;
        }

        List<String> missing = new ArrayList<>();
        if (value.chars().noneMatch(Character::isUpperCase)) {
            missing.add("one uppercase letter");
        }
        if (value.chars().noneMatch(Character::isLowerCase)) {
            missing.add("one lowercase letter");
        }
        if (value.chars().noneMatch(Character::isDigit)) {
            missing.add("one digit");
        }
        if (value.chars().allMatch(Character::isLetterOrDigit)) {
            missing.add("one special character");
        }

        if (missing.isEmpty()) {
            return true;
        }

        context.disableDefaultConstraintViolation();
        context.buildConstraintViolationWithTemplate(
                        "Password must contain at least " + String.join(", ", missing))
                .addConstraintViolation();
        return false;
    }
}
