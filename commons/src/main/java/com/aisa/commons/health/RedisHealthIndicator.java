package com.aisa.commons.health;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.HealthIndicator;
import org.springframework.data.redis.connection.RedisConnection;
import org.springframework.data.redis.connection.RedisConnectionFactory;

/**
 * Custom health indicator that verifies Redis connectivity.
 *
 * <p>Reports UP when a PING command returns PONG within the K8s probe timeout budget,
 * DOWN otherwise. Used by Spring Boot Actuator's readiness probe to indicate whether
 * this service instance can access its Redis-backed cache/session store.
 *
 * <p>Spring Boot's built-in Redis health indicator exists but does not always participate
 * in the readiness group by default. This indicator is explicitly added to the readiness
 * group via application.yml configuration.
 *
 * <p>Requirements: 28.7 (health-check signal per service, ≤10s evaluation)
 */
public class RedisHealthIndicator implements HealthIndicator {

    private static final Logger log = LoggerFactory.getLogger(RedisHealthIndicator.class);

    private final RedisConnectionFactory connectionFactory;

    public RedisHealthIndicator(RedisConnectionFactory connectionFactory) {
        this.connectionFactory = connectionFactory;
    }

    @Override
    public Health health() {
        try (RedisConnection connection = connectionFactory.getConnection()) {
            String pong = connection.ping();
            if ("PONG".equalsIgnoreCase(pong)) {
                return Health.up()
                        .withDetail("response", pong)
                        .build();
            }
            return Health.down()
                    .withDetail("response", pong)
                    .build();
        } catch (Exception e) {
            log.warn("Redis health check failed: {}", e.getMessage());
            return Health.down()
                    .withDetail("error", e.getMessage())
                    .build();
        }
    }
}
