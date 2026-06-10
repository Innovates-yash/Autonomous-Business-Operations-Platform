package com.aisa.audit.messaging;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.HashMap;
import java.util.Map;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.apache.kafka.common.serialization.StringSerializer;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.ConcurrentKafkaListenerContainerFactory;
import org.springframework.kafka.core.ConsumerFactory;
import org.springframework.kafka.core.DefaultKafkaConsumerFactory;
import org.springframework.kafka.core.DefaultKafkaProducerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.core.ProducerFactory;
import org.springframework.kafka.support.serializer.JsonDeserializer;
import org.springframework.kafka.support.serializer.JsonSerializer;

/**
 * Kafka wiring for audit ingestion and rejection signalling.
 *
 * <p>Both sides use JSON built from the Spring-managed {@link ObjectMapper}, which
 * has the JSR-310 module registered, so {@code Instant} timestamps (Req 23.1)
 * round-trip as ISO-8601 strings. The consumer is pinned to deserialize the
 * inbound payload as {@link AuditEventMessage} regardless of any type headers a
 * producer may set, keeping the contract stable across services.
 */
@Configuration
public class AuditKafkaConfig {

    private final String bootstrapServers;
    private final String groupId;
    private final String autoOffsetReset;

    public AuditKafkaConfig(
            @Value("${spring.kafka.bootstrap-servers:localhost:9092}") String bootstrapServers,
            @Value("${spring.kafka.consumer.group-id:audit-service}") String groupId,
            @Value("${spring.kafka.consumer.auto-offset-reset:earliest}") String autoOffsetReset) {
        this.bootstrapServers = bootstrapServers;
        this.groupId = groupId;
        this.autoOffsetReset = autoOffsetReset;
    }

    @Bean
    public ConsumerFactory<String, AuditEventMessage> auditConsumerFactory(ObjectMapper objectMapper) {
        Map<String, Object> props = new HashMap<>();
        props.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);
        props.put(ConsumerConfig.GROUP_ID_CONFIG, groupId);
        props.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, autoOffsetReset);
        props.put(ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG, false);

        JsonDeserializer<AuditEventMessage> valueDeserializer =
                new JsonDeserializer<>(AuditEventMessage.class, objectMapper, false);
        valueDeserializer.setUseTypeHeaders(false);
        valueDeserializer.addTrustedPackages("com.aisa.audit.messaging");

        return new DefaultKafkaConsumerFactory<>(props, new StringDeserializer(), valueDeserializer);
    }

    @Bean
    public ConcurrentKafkaListenerContainerFactory<String, AuditEventMessage> kafkaListenerContainerFactory(
            ConsumerFactory<String, AuditEventMessage> auditConsumerFactory) {
        ConcurrentKafkaListenerContainerFactory<String, AuditEventMessage> factory =
                new ConcurrentKafkaListenerContainerFactory<>();
        factory.setConsumerFactory(auditConsumerFactory);
        return factory;
    }

    @Bean
    public ProducerFactory<String, Object> auditProducerFactory(ObjectMapper objectMapper) {
        Map<String, Object> props = new HashMap<>();
        props.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);
        // Durability for the rejection signal that gates the originating action (Req 23.2).
        props.put(ProducerConfig.ACKS_CONFIG, "all");
        props.put(ProducerConfig.ENABLE_IDEMPOTENCE_CONFIG, true);
        DefaultKafkaProducerFactory<String, Object> factory =
                new DefaultKafkaProducerFactory<>(props, new StringSerializer(),
                        new JsonSerializer<>(objectMapper));
        return factory;
    }

    @Bean
    public KafkaTemplate<String, Object> kafkaTemplate(ProducerFactory<String, Object> auditProducerFactory) {
        return new KafkaTemplate<>(auditProducerFactory);
    }
}
