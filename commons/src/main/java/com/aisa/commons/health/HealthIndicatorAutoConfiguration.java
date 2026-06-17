package com.aisa.commons.health;

import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.context.annotation.Bean;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.kafka.core.KafkaAdmin;

/**
 * Auto-configuration that registers custom health indicators for Kafka and Redis.
 *
 * <p>Each indicator is only created when the corresponding infrastructure client bean
 * is present in the application context, so services that do not use Kafka or Redis
 * (e.g., Export Service) are unaffected.
 *
 * <p>These health indicators participate in Spring Boot Actuator's readiness probe group
 * (configured via application.yml: management.endpoint.health.group.readiness.include).
 * The K8s readiness probe targets {@code /actuator/health/readiness}, ensuring that an
 * instance with a broken Kafka or Redis connection is removed from the service mesh
 * routing within the ≤30s requirement (Requirement 28.9).
 *
 * <p>Requirements: 28.7 (health-check signal ≤30s interval, ≤10s timeout),
 *                  28.8 (3 consecutive failures → unhealthy classification),
 *                  28.9 (route traffic away from unhealthy within 30s)
 *
 * @see KafkaHealthIndicator
 * @see RedisHealthIndicator
 */
@AutoConfiguration
@ConditionalOnClass(name = "org.springframework.boot.actuate.health.HealthIndicator")
public class HealthIndicatorAutoConfiguration {

    /**
     * Registers a Kafka health indicator when a {@link KafkaAdmin} bean is available.
     */
    @Bean
    @ConditionalOnClass(KafkaAdmin.class)
    @ConditionalOnBean(KafkaAdmin.class)
    public KafkaHealthIndicator kafkaHealthIndicator(KafkaAdmin kafkaAdmin) {
        return new KafkaHealthIndicator(kafkaAdmin);
    }

    /**
     * Registers a Redis health indicator when a {@link RedisConnectionFactory} bean is available.
     */
    @Bean
    @ConditionalOnClass(RedisConnectionFactory.class)
    @ConditionalOnBean(RedisConnectionFactory.class)
    public RedisHealthIndicator redisHealthIndicator(RedisConnectionFactory connectionFactory) {
        return new RedisHealthIndicator(connectionFactory);
    }
}
