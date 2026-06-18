package com.aisa.commons.redis;

import java.time.Duration;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Configuration properties for the shared Redis cache auto-configuration.
 *
 * <p>These properties allow each service to customize its cache behavior without
 * writing a custom {@code @Configuration} class. Default values are chosen for
 * the typical read-cache pattern (5-minute TTL, service name from
 * {@code spring.application.name}).
 *
 * <h2>Property Reference</h2>
 * <table>
 *   <tr><th>Property</th><th>Default</th><th>Description</th></tr>
 *   <tr>
 *     <td>{@code aisa.redis.cache.enabled}</td><td>{@code true}</td>
 *     <td>Enable/disable the auto-configured RedisCacheManager</td>
 *   </tr>
 *   <tr>
 *     <td>{@code aisa.redis.cache.service-name}</td><td>{@code default}</td>
 *     <td>Service name used as key prefix: {@code aisa:cache:<service-name>:}</td>
 *   </tr>
 *   <tr>
 *     <td>{@code aisa.redis.cache.default-ttl}</td><td>{@code PT5M} (5 minutes)</td>
 *     <td>Default cache entry TTL (ISO-8601 duration)</td>
 *   </tr>
 * </table>
 *
 * @see RedisCacheAutoConfiguration
 */
@ConfigurationProperties(prefix = "aisa.redis.cache")
public class RedisCacheProperties {

    /**
     * Whether the shared Redis cache auto-configuration is enabled.
     * Set to {@code false} to disable auto-configuration entirely.
     */
    private boolean enabled = true;

    /**
     * Service name used as the key-prefix namespace: {@code aisa:cache:<service-name>:}.
     * Defaults to "default"; each service should set this to its own name
     * (typically via {@code ${spring.application.name}}).
     */
    private String serviceName = "default";

    /**
     * Default TTL for cache entries. ISO-8601 duration format.
     * Default: 5 minutes (PT5M).
     */
    private Duration defaultTtl = Duration.ofMinutes(5);

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public String getServiceName() {
        return serviceName;
    }

    public void setServiceName(String serviceName) {
        this.serviceName = serviceName;
    }

    public Duration getDefaultTtl() {
        return defaultTtl;
    }

    public void setDefaultTtl(Duration defaultTtl) {
        this.defaultTtl = defaultTtl;
    }
}
