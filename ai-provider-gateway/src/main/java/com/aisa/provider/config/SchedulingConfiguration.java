package com.aisa.provider.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * Enables the periodic selection-cache refresh that lets a saved provider selection take effect
 * across stateless instances within 5 seconds (Requirement 20.2).
 */
@Configuration
@EnableScheduling
public class SchedulingConfiguration {
}
