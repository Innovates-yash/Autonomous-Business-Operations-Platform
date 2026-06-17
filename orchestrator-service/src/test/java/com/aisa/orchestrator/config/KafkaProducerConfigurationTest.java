package com.aisa.orchestrator.config;

import org.apache.kafka.clients.producer.ProducerConfig;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.kafka.KafkaProperties;
import org.springframework.kafka.core.ProducerFactory;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for {@link KafkaProducerConfiguration}.
 * Validates: Requirements 26.1, 26.5, 26.6 — reliable producer with acks=all and idempotence.
 */
class KafkaProducerConfigurationTest {

    @Test
    @DisplayName("producer factory is configured with acks=all for durability (Req 26.1)")
    void producerFactory_hasAcksAll() {
        KafkaProperties kafkaProperties = new KafkaProperties();
        kafkaProperties.setBootstrapServers(java.util.List.of("localhost:9092"));
        KafkaProducerConfiguration config = new KafkaProducerConfiguration(kafkaProperties);

        ProducerFactory<String, Object> factory = config.producerFactory();
        Map<String, Object> configs = factory.getConfigurationProperties();

        assertThat(configs.get(ProducerConfig.ACKS_CONFIG)).isEqualTo("all");
    }

    @Test
    @DisplayName("producer factory has idempotence enabled for exactly-once per partition (Req 26.1)")
    void producerFactory_hasIdempotenceEnabled() {
        KafkaProperties kafkaProperties = new KafkaProperties();
        kafkaProperties.setBootstrapServers(java.util.List.of("localhost:9092"));
        KafkaProducerConfiguration config = new KafkaProducerConfiguration(kafkaProperties);

        ProducerFactory<String, Object> factory = config.producerFactory();
        Map<String, Object> configs = factory.getConfigurationProperties();

        assertThat(configs.get(ProducerConfig.ENABLE_IDEMPOTENCE_CONFIG)).isEqualTo(true);
    }

    @Test
    @DisplayName("producer delivery timeout is bounded to 2s for acknowledgment window (Req 26.1)")
    void producerFactory_hasDeliveryTimeoutBoundedTo2Seconds() {
        KafkaProperties kafkaProperties = new KafkaProperties();
        kafkaProperties.setBootstrapServers(java.util.List.of("localhost:9092"));
        KafkaProducerConfiguration config = new KafkaProducerConfiguration(kafkaProperties);

        ProducerFactory<String, Object> factory = config.producerFactory();
        Map<String, Object> configs = factory.getConfigurationProperties();

        assertThat(configs.get(ProducerConfig.DELIVERY_TIMEOUT_MS_CONFIG)).isEqualTo(2000);
    }

    @Test
    @DisplayName("producer retries are configured for bounded retry attempts")
    void producerFactory_hasRetriesConfigured() {
        KafkaProperties kafkaProperties = new KafkaProperties();
        kafkaProperties.setBootstrapServers(java.util.List.of("localhost:9092"));
        KafkaProducerConfiguration config = new KafkaProducerConfiguration(kafkaProperties);

        ProducerFactory<String, Object> factory = config.producerFactory();
        Map<String, Object> configs = factory.getConfigurationProperties();

        assertThat(configs.get(ProducerConfig.RETRIES_CONFIG)).isEqualTo(3);
    }
}
