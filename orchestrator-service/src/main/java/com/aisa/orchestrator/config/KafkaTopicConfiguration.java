package com.aisa.orchestrator.config;

import org.apache.kafka.clients.admin.NewTopic;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.TopicBuilder;

/**
 * Declares the four core Kafka topics used by the orchestrator and across the platform.
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

    /** Topic for agent work items — high-throughput, parallel consumers. */
    public static final String TOPIC_AGENT_TASKS = "agent-tasks";

    /** Topic for agent progress events — fan-out to notification service. */
    public static final String TOPIC_AGENT_PROGRESS = "agent-progress";

    /** Topic for project state change events. */
    public static final String TOPIC_PROJECT_STATE_CHANGES = "project-state-changes";

    /** Topic for audit events — append-only immutable log. */
    public static final String TOPIC_AUDIT_EVENTS = "audit-events";

    @Bean
    public NewTopic agentTasksTopic() {
        return TopicBuilder.name(TOPIC_AGENT_TASKS)
                .partitions(6)
                .replicas(1)
                .build();
    }

    @Bean
    public NewTopic agentProgressTopic() {
        return TopicBuilder.name(TOPIC_AGENT_PROGRESS)
                .partitions(3)
                .replicas(1)
                .build();
    }

    @Bean
    public NewTopic projectStateChangesTopic() {
        return TopicBuilder.name(TOPIC_PROJECT_STATE_CHANGES)
                .partitions(3)
                .replicas(1)
                .build();
    }

    @Bean
    public NewTopic auditEventsTopic() {
        return TopicBuilder.name(TOPIC_AUDIT_EVENTS)
                .partitions(3)
                .replicas(1)
                .build();
    }
}
