package com.aisa.commons.kafka;

/**
 * Shared Kafka topic name constants used across all platform services.
 * <p>
 * Centralizing topic names in commons ensures consistency and avoids
 * hardcoded strings scattered across independent microservices.
 * <ul>
 *   <li>{@link #AGENT_TASKS} — work items dispatched to agent workers (Req 6, 26.1)</li>
 *   <li>{@link #AGENT_PROGRESS} — per-agent state-change events (Req 6.7, 22)</li>
 *   <li>{@link #PROJECT_STATE_CHANGES} — project lifecycle transitions (Req 3.8, 22)</li>
 *   <li>{@link #AUDIT_EVENTS} — immutable audit records (Req 23)</li>
 * </ul>
 */
public final class KafkaTopics {

    private KafkaTopics() {
        // Utility class — prevent instantiation
    }

    /** Topic for agent work items — high-throughput, parallel consumers. */
    public static final String AGENT_TASKS = "agent-tasks";

    /** Topic for agent progress events — fan-out to notification service. */
    public static final String AGENT_PROGRESS = "agent-progress";

    /** Topic for project state change events. */
    public static final String PROJECT_STATE_CHANGES = "project-state-changes";

    /** Topic for audit events — append-only immutable log. */
    public static final String AUDIT_EVENTS = "audit-events";
}
