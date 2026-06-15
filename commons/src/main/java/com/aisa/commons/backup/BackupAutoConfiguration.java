package com.aisa.commons.backup;

import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.context.properties.EnableConfigurationProperties;

/**
 * Auto-configuration that exposes {@link BackupProperties} as a Spring-managed bean.
 *
 * <p>Any service that depends on commons will automatically have access to the backup
 * configuration properties bound from {@code aisa.backup.*} in its application.yml.
 * This enables Java-based components (e.g., admin dashboards, monitoring endpoints)
 * to programmatically reference the Platform's backup policy settings.
 *
 * <p>Registered via
 * {@code META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports}.
 *
 * <p>Requirements: 28.1, 28.2, 28.4, 28.5
 */
@AutoConfiguration
@EnableConfigurationProperties(BackupProperties.class)
public class BackupAutoConfiguration {
}
