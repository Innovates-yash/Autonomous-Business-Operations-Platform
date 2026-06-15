package com.aisa.commons.kafka;

import org.apache.kafka.clients.admin.NewTopic;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.context.annotation.Bean;

/**
 * Auto-configuration that ensures the platform's Kafka topics exist.
 *
 * <p>When spring-kafka's {@code KafkaAdmin} is on the classpath, Spring Boot automatically
 * provisions any {@link NewTopic} beans at startup via the admin client. This class defines
 * one {@link NewTopic} per platform topic with sensible defaults (3 partitions, replication
 * factor 1 for dev — overridden by broker defaults in production).
 *
 * <p>Registered via
 * {@code META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports}
 * so every service that depends on commons and has spring-kafka on its classpath will
 * automatically create/verify the topics.
 *
 * <p>Requirements: 26.1, 26.5, 26.6
 */
@AutoConfiguration
@ConditionalOnClass(name = "org.springframework.kafka.core.KafkaAdmin")
public class KafkaTopicAutoConfiguration {

    private static final int DEFAULT_PARTITIONS = 3;
    private static final short DEFAULT_REPLICATION_FACTOR = 1;

    @Bean
    public NewTopic agentTasksTopic() {
        return new NewTopic(KafkaTopics.AGENT_TASKS, DEFAULT_PARTITIONS, DEFAULT_REPLICATION_FACTOR);
    }

    @Bean
    public NewTopic agentProgressTopic() {
        return new NewTopic(KafkaTopics.AGENT_PROGRESS, DEFAULT_PARTITIONS, DEFAULT_REPLICATION_FACTOR);
    }

    @Bean
    public NewTopic projectStateChangesTopic() {
        return new NewTopic(KafkaTopics.PROJECT_STATE_CHANGES, DEFAULT_PARTITIONS, DEFAULT_REPLICATION_FACTOR);
    }

    @Bean
    public NewTopic auditEventsTopic() {
        return new NewTopic(KafkaTopics.AUDIT_EVENTS, DEFAULT_PARTITIONS, DEFAULT_REPLICATION_FACTOR);
    }
}
