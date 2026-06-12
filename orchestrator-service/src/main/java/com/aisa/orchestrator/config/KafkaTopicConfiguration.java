package com.aisa.orchestrator.config;

import com.aisa.commons.kafka.KafkaTopics;
import org.apache.kafka.clients.admin.NewTopic;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.TopicBuilder;

/**
 * Declares the four core Kafka topics used by the orchestrator and across the platform.
 * <p>
 * Topic names are sourced from the shared {@link KafkaTopics} constants in commons
 * to ensure consistency across all services.
 * <ul>
 *   <li><b>agent-tasks</b> — work items dispatched to agent workers (Req 6, 26.1)</li>
 *   <li><b>agent-progress</b> — per-agent state-change events consumed by notification service (Req 6.7, 22)</li>
 *   <li><b>project-state-changes</b> — project lifecycle transitions (Req 3.8, 22)</li>
 *   <li><b>audit-events</b> — immutable audit records (Req 23)</li>
 * </ul>
 *
 * Partition counts balance parallelism with cluster size appropriate for production.
 * Replication factor defaults to 1 for local dev; production Kafka clusters should override
 * via broker-level min.insync.replicas and topic-level overrides.
 */
@Configuration
public class KafkaTopicConfiguration {

    /**
     * Topic for agent work items — high-throughput, parallel consumers.
     * @deprecated Use {@link KafkaTopics#AGENT_TASKS} directly for topic name references.
     */
    @Deprecated(forRemoval = true)
    public static final String TOPIC_AGENT_TASKS = KafkaTopics.AGENT_TASKS;

    /**
     * Topic for agent progress events — fan-out to notification service.
     * @deprecated Use {@link KafkaTopics#AGENT_PROGRESS} directly for topic name references.
     */
    @Deprecated(forRemoval = true)
    public static final String TOPIC_AGENT_PROGRESS = KafkaTopics.AGENT_PROGRESS;

    /**
     * Topic for project state change events.
     * @deprecated Use {@link KafkaTopics#PROJECT_STATE_CHANGES} directly for topic name references.
     */
    @Deprecated(forRemoval = true)
    public static final String TOPIC_PROJECT_STATE_CHANGES = KafkaTopics.PROJECT_STATE_CHANGES;

    /**
     * Topic for audit events — append-only immutable log.
     * @deprecated Use {@link KafkaTopics#AUDIT_EVENTS} directly for topic name references.
     */
    @Deprecated(forRemoval = true)
    public static final String TOPIC_AUDIT_EVENTS = KafkaTopics.AUDIT_EVENTS;

    @Bean
    public NewTopic agentTasksTopic() {
        return TopicBuilder.name(KafkaTopics.AGENT_TASKS)
                .partitions(6)
                .replicas(1)
                .build();
    }

    @Bean
    public NewTopic agentProgressTopic() {
        return TopicBuilder.name(KafkaTopics.AGENT_PROGRESS)
                .partitions(3)
                .replicas(1)
                .build();
    }

    @Bean
    public NewTopic projectStateChangesTopic() {
        return TopicBuilder.name(KafkaTopics.PROJECT_STATE_CHANGES)
                .partitions(3)
                .replicas(1)
                .build();
    }

    @Bean
    public NewTopic auditEventsTopic() {
        return TopicBuilder.name(KafkaTopics.AUDIT_EVENTS)
                .partitions(3)
                .replicas(1)
                .build();
    }
}
