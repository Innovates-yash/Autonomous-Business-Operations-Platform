package com.aisa.commons.kafka;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Constructor;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for {@link KafkaTopics} shared constants.
 */
class KafkaTopicsTest {

    @Test
    @DisplayName("AGENT_TASKS constant matches expected topic name")
    void agentTasks_hasCorrectValue() {
        assertThat(KafkaTopics.AGENT_TASKS).isEqualTo("agent-tasks");
    }

    @Test
    @DisplayName("AGENT_PROGRESS constant matches expected topic name")
    void agentProgress_hasCorrectValue() {
        assertThat(KafkaTopics.AGENT_PROGRESS).isEqualTo("agent-progress");
    }

    @Test
    @DisplayName("PROJECT_STATE_CHANGES constant matches expected topic name")
    void projectStateChanges_hasCorrectValue() {
        assertThat(KafkaTopics.PROJECT_STATE_CHANGES).isEqualTo("project-state-changes");
    }

    @Test
    @DisplayName("AUDIT_EVENTS constant matches expected topic name")
    void auditEvents_hasCorrectValue() {
        assertThat(KafkaTopics.AUDIT_EVENTS).isEqualTo("audit-events");
    }

    @Test
    @DisplayName("KafkaTopics has private constructor (utility class)")
    void hasPrivateConstructor() throws NoSuchMethodException {
        Constructor<KafkaTopics> constructor = KafkaTopics.class.getDeclaredConstructor();
        assertThat(java.lang.reflect.Modifier.isPrivate(constructor.getModifiers())).isTrue();
    }
}
