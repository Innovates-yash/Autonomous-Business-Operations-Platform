package com.aisa.orchestrator.config;

import com.aisa.commons.kafka.KafkaTopics;
import org.apache.kafka.clients.admin.NewTopic;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for {@link KafkaTopicConfiguration}.
 * Validates that the 4 required Kafka topics are defined with appropriate settings
 * and reference the shared {@link KafkaTopics} constants from commons.
 */
class KafkaTopicConfigurationTest {

    private final KafkaTopicConfiguration config = new KafkaTopicConfiguration();

    @Test
    @DisplayName("agent-tasks topic is configured with correct name and partitions")
    void agentTasksTopic_hasCorrectConfig() {
        NewTopic topic = config.agentTasksTopic();

        assertThat(topic.name()).isEqualTo(KafkaTopics.AGENT_TASKS);
        assertThat(topic.numPartitions()).isEqualTo(6);
        assertThat(topic.replicationFactor()).isEqualTo((short) 1);
    }

    @Test
    @DisplayName("agent-progress topic is configured with correct name and partitions")
    void agentProgressTopic_hasCorrectConfig() {
        NewTopic topic = config.agentProgressTopic();

        assertThat(topic.name()).isEqualTo(KafkaTopics.AGENT_PROGRESS);
        assertThat(topic.numPartitions()).isEqualTo(3);
        assertThat(topic.replicationFactor()).isEqualTo((short) 1);
    }

    @Test
    @DisplayName("project-state-changes topic is configured with correct name and partitions")
    void projectStateChangesTopic_hasCorrectConfig() {
        NewTopic topic = config.projectStateChangesTopic();

        assertThat(topic.name()).isEqualTo(KafkaTopics.PROJECT_STATE_CHANGES);
        assertThat(topic.numPartitions()).isEqualTo(3);
        assertThat(topic.replicationFactor()).isEqualTo((short) 1);
    }

    @Test
    @DisplayName("audit-events topic is configured with correct name and partitions")
    void auditEventsTopic_hasCorrectConfig() {
        NewTopic topic = config.auditEventsTopic();

        assertThat(topic.name()).isEqualTo(KafkaTopics.AUDIT_EVENTS);
        assertThat(topic.numPartitions()).isEqualTo(3);
        assertThat(topic.replicationFactor()).isEqualTo((short) 1);
    }

    @SuppressWarnings("deprecation")
    @Test
    @DisplayName("deprecated constants delegate to shared KafkaTopics")
    void deprecatedConstants_delegateToSharedKafkaTopics() {
        assertThat(KafkaTopicConfiguration.TOPIC_AGENT_TASKS).isEqualTo(KafkaTopics.AGENT_TASKS);
        assertThat(KafkaTopicConfiguration.TOPIC_AGENT_PROGRESS).isEqualTo(KafkaTopics.AGENT_PROGRESS);
        assertThat(KafkaTopicConfiguration.TOPIC_PROJECT_STATE_CHANGES).isEqualTo(KafkaTopics.PROJECT_STATE_CHANGES);
        assertThat(KafkaTopicConfiguration.TOPIC_AUDIT_EVENTS).isEqualTo(KafkaTopics.AUDIT_EVENTS);
    }
}
