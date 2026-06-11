package com.aisa.orchestrator.config;

import org.apache.kafka.clients.admin.NewTopic;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for {@link KafkaTopicConfiguration}.
 * Validates that the 4 required Kafka topics are defined with appropriate settings.
 */
class KafkaTopicConfigurationTest {

    private final KafkaTopicConfiguration config = new KafkaTopicConfiguration();

    @Test
    @DisplayName("agent-tasks topic is configured with correct name and partitions")
    void agentTasksTopic_hasCorrectConfig() {
        NewTopic topic = config.agentTasksTopic();

        assertThat(topic.name()).isEqualTo("agent-tasks");
        assertThat(topic.numPartitions()).isEqualTo(6);
        assertThat(topic.replicationFactor()).isEqualTo((short) 1);
    }

    @Test
    @DisplayName("agent-progress topic is configured with correct name and partitions")
    void agentProgressTopic_hasCorrectConfig() {
        NewTopic topic = config.agentProgressTopic();

        assertThat(topic.name()).isEqualTo("agent-progress");
        assertThat(topic.numPartitions()).isEqualTo(3);
        assertThat(topic.replicationFactor()).isEqualTo((short) 1);
    }

    @Test
    @DisplayName("project-state-changes topic is configured with correct name and partitions")
    void projectStateChangesTopic_hasCorrectConfig() {
        NewTopic topic = config.projectStateChangesTopic();

        assertThat(topic.name()).isEqualTo("project-state-changes");
        assertThat(topic.numPartitions()).isEqualTo(3);
        assertThat(topic.replicationFactor()).isEqualTo((short) 1);
    }

    @Test
    @DisplayName("audit-events topic is configured with correct name and partitions")
    void auditEventsTopic_hasCorrectConfig() {
        NewTopic topic = config.auditEventsTopic();

        assertThat(topic.name()).isEqualTo("audit-events");
        assertThat(topic.numPartitions()).isEqualTo(3);
        assertThat(topic.replicationFactor()).isEqualTo((short) 1);
    }

    @Test
    @DisplayName("all four topic constants are defined")
    void topicConstants_areDefined() {
        assertThat(KafkaTopicConfiguration.TOPIC_AGENT_TASKS).isEqualTo("agent-tasks");
        assertThat(KafkaTopicConfiguration.TOPIC_AGENT_PROGRESS).isEqualTo("agent-progress");
        assertThat(KafkaTopicConfiguration.TOPIC_PROJECT_STATE_CHANGES).isEqualTo("project-state-changes");
        assertThat(KafkaTopicConfiguration.TOPIC_AUDIT_EVENTS).isEqualTo("audit-events");
    }
}
