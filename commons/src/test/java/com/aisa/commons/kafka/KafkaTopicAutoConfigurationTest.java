package com.aisa.commons.kafka;

import org.apache.kafka.clients.admin.NewTopic;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for {@link KafkaTopicAutoConfiguration}.
 *
 * <p>Validates that topic beans are created with correct names (Requirement 26.1).
 */
class KafkaTopicAutoConfigurationTest {

    private final KafkaTopicAutoConfiguration config = new KafkaTopicAutoConfiguration();

    @Test
    @DisplayName("agentTasksTopic bean has correct name and partitions")
    void agentTasksTopic_hasCorrectConfig() {
        NewTopic topic = config.agentTasksTopic();
        assertThat(topic.name()).isEqualTo("agent-tasks");
        assertThat(topic.numPartitions()).isEqualTo(3);
        assertThat(topic.replicationFactor()).isEqualTo((short) 1);
    }

    @Test
    @DisplayName("agentProgressTopic bean has correct name and partitions")
    void agentProgressTopic_hasCorrectConfig() {
        NewTopic topic = config.agentProgressTopic();
        assertThat(topic.name()).isEqualTo("agent-progress");
        assertThat(topic.numPartitions()).isEqualTo(3);
    }

    @Test
    @DisplayName("projectStateChangesTopic bean has correct name and partitions")
    void projectStateChangesTopic_hasCorrectConfig() {
        NewTopic topic = config.projectStateChangesTopic();
        assertThat(topic.name()).isEqualTo("project-state-changes");
        assertThat(topic.numPartitions()).isEqualTo(3);
    }

    @Test
    @DisplayName("auditEventsTopic bean has correct name and partitions")
    void auditEventsTopic_hasCorrectConfig() {
        NewTopic topic = config.auditEventsTopic();
        assertThat(topic.name()).isEqualTo("audit-events");
        assertThat(topic.numPartitions()).isEqualTo(3);
    }
}
