package com.aisa.auth.validation;

import static org.assertj.core.api.Assertions.assertThat;

import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;
import java.util.Set;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;

/**
 * Unit tests for {@link PasswordComplexityValidator}, covering the four
 * character-class rules of Requirement 1.1 / 1.12.
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class PasswordComplexityValidatorTest {

    private ValidatorFactory factory;
    private Validator validator;

    /** Test holder bean exercising the constraint in isolation. */
    private record Holder(@PasswordComplexity String password) {
    }

    @BeforeAll
    void setUp() {
        factory = Validation.buildDefaultValidatorFactory();
        validator = factory.getValidator();
    }

    @AfterAll
    void tearDown() {
        factory.close();
    }

    private Set<jakarta.validation.ConstraintViolation<Holder>> validate(String password) {
        return validator.validate(new Holder(password));
    }

    @Test
    void acceptsPasswordWithAllFourCharacterClasses() {
        assertThat(validate("Abcdefg1!xyz")).isEmpty();
    }

    @Test
    void rejectsPasswordMissingUppercase() {
        var violations = validate("abcdefg1!xyz");
        assertThat(violations).hasSize(1);
        assertThat(violations.iterator().next().getMessage())
                .contains("one uppercase letter");
    }

    @Test
    void rejectsPasswordMissingLowercase() {
        var violations = validate("ABCDEFG1!XYZ");
        assertThat(violations).hasSize(1);
        assertThat(violations.iterator().next().getMessage())
                .contains("one lowercase letter");
    }

    @Test
    void rejectsPasswordMissingDigit() {
        var violations = validate("Abcdefgh!xyz");
        assertThat(violations).hasSize(1);
        assertThat(violations.iterator().next().getMessage())
                .contains("one digit");
    }

    @Test
    void rejectsPasswordMissingSpecialCharacter() {
        var violations = validate("Abcdefg12xyz");
        assertThat(violations).hasSize(1);
        assertThat(violations.iterator().next().getMessage())
                .contains("one special character");
    }

    @Test
    void namesEveryMissingClassWhenSeveralAreAbsent() {
        var violations = validate("aaaaaaaaaaaa");
        assertThat(violations).hasSize(1);
        String message = violations.iterator().next().getMessage();
        assertThat(message)
                .contains("one uppercase letter")
                .contains("one digit")
                .contains("one special character");
    }

    @Test
    void skipsComplexityCheckWhenBlankSoOtherConstraintsReport() {
        // Null/empty is the responsibility of @NotBlank, not this validator.
        assertThat(validate(null)).isEmpty();
        assertThat(validate("")).isEmpty();
    }
}
