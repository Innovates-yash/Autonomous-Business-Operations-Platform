package com.aisa.commons.kafka;

import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.context.annotation.Bean;
import org.springframework.kafka.core.KafkaTemplate;

/**
 * Auto-configuration that creates a {@link KafkaSubmissionService} bean when a
 * {@link KafkaTemplate} is available in the application context.
 *
 * <p>Services that include spring-kafka and commons will automatically get the
 * submission service with ≤2s ack timeout and Kafka-unavailable exception semantics.
 *
 * <p>Requirements: 26.5
 */
@AutoConfiguration(after = KafkaTopicAutoConfiguration.class)
@ConditionalOnClass(KafkaTemplate.class)
public class KafkaSubmissionAutoConfiguration {

    @Bean
    @ConditionalOnBean(KafkaTemplate.class)
    public KafkaSubmissionService kafkaSubmissionService(KafkaTemplate<String, Object> kafkaTemplate) {
        return new KafkaSubmissionService(kafkaTemplate);
    }
}
