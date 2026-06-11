package com.aisa.commons.error;

import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.context.annotation.ComponentScan;

/**
 * Auto-configuration that registers the shared {@link GlobalExceptionHandler} in every
 * servlet-based service that depends on the commons library.
 *
 * <p>This ensures Requirement 25.6 (input validation with safe errors) and
 * Requirement 25.7 (no internal detail leaks) are enforced uniformly across all services
 * without each service needing to declare the handler explicitly.
 *
 * <p>Service-specific {@code @RestControllerAdvice} handlers with higher precedence
 * still take priority for their domain exceptions.
 */
@AutoConfiguration
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
@ComponentScan(basePackageClasses = GlobalExceptionHandler.class)
public class ValidationAutoConfiguration {
}
