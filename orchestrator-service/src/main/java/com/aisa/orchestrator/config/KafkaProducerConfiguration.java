package com.aisa.orchestrator.config;

import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.common.serialization.StringSerializer;
import org.springframework.boot.autoconfigure.kafka.KafkaProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.core.DefaultKafkaProducerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.core.ProducerFactory;
import org.springframework.kafka.support.serializer.JsonSerializer;

import java.util.Map;

/**
 * Kafka producer configuration with reliability guarantees (Requirements 26.1, 26.5, 26.6):
 * <ul>
 *   <li>{@code acks=all} — wait for all in-sync replicas to acknowledge the write</li>
 *   <li>{@code enable.idempotence=true} — prevent duplicate messages on retry</li>
 *   <li>{@code retries=3} with {@code delivery.timeout.ms=2000} — bounded retries within the
 *       2-second acknowledgment window (Req 26.1)</li>
 * </ul>
 *
 * <p>Idempotent production guarantees exactly-once semantics per partition, which prevents
 * duplicate agent task submissions during transient network failures. Combined with
 * {@code acks=all}, data is durably committed before the producer considers the write successful.
 */
@Configuration
public class KafkaProducerConfiguration {

    private final KafkaProperties kafkaProperties;

    public KafkaProducerConfiguration(KafkaProperties kafkaProperties) {
        this.kafkaProperties = kafkaProperties;
    }

    @Bean
    public ProducerFactory<String, Object> producerFactory() {
        Map<String, Object> props = kafkaProperties.buildProducerProperties(null);

        // Reliability: all ISR replicas must acknowledge (Req 26.1).
        props.put(ProducerConfig.ACKS_CONFIG, "all");

        // Idempotence: prevent duplicates on retry (exactly-once per partition).
        props.put(ProducerConfig.ENABLE_IDEMPOTENCE_CONFIG, true);

        // Bounded retries within the 2s acknowledgment window.
        props.put(ProducerConfig.RETRIES_CONFIG, 3);
        props.put(ProducerConfig.DELIVERY_TIMEOUT_MS_CONFIG, 2000);
        props.put(ProducerConfig.REQUEST_TIMEOUT_MS_CONFIG, 1500);
        props.put(ProducerConfig.LINGER_MS_CONFIG, 0);

        // Serializers
        props.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class);
        props.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, JsonSerializer.class);

        return new DefaultKafkaProducerFactory<>(props);
    }

    @Bean
    public KafkaTemplate<String, Object> kafkaTemplate(ProducerFactory<String, Object> producerFactory) {
        return new KafkaTemplate<>(producerFactory);
    }
}
