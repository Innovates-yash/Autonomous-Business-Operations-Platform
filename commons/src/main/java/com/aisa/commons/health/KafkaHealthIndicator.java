package com.aisa.commons.health;

import org.apache.kafka.clients.admin.AdminClient;
import org.apache.kafka.clients.admin.DescribeClusterOptions;
import org.apache.kafka.clients.admin.DescribeClusterResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.HealthIndicator;
import org.springframework.kafka.core.KafkaAdmin;

import java.util.concurrent.TimeUnit;

/**
 * Custom health indicator that verifies Kafka broker connectivity.
 *
 * <p>Reports UP when the cluster metadata is reachable within the configured timeout,
 * DOWN otherwise. Used by Spring Boot Actuator's readiness probe to indicate whether
 * this service instance can process Kafka-dependent requests.
 *
 * <p>The health check completes within the K8s probe timeout budget (≤10s) by using
 * a 5-second internal timeout for the cluster describe operation.
 *
 * <p>Requirements: 28.7 (health-check signal per service, ≤10s evaluation)
 */
public class KafkaHealthIndicator implements HealthIndicator {

    private static final Logger log = LoggerFactory.getLogger(KafkaHealthIndicator.class);

    /** Internal timeout for Kafka admin operations — well within the 10s K8s probe timeout. */
    private static final int TIMEOUT_SECONDS = 5;

    private final KafkaAdmin kafkaAdmin;

    public KafkaHealthIndicator(KafkaAdmin kafkaAdmin) {
        this.kafkaAdmin = kafkaAdmin;
    }

    @Override
    public Health health() {
        try (AdminClient adminClient = AdminClient.create(kafkaAdmin.getConfigurationProperties())) {
            DescribeClusterOptions options = new DescribeClusterOptions()
                    .timeoutMs((int) TimeUnit.SECONDS.toMillis(TIMEOUT_SECONDS));
            DescribeClusterResult result = adminClient.describeCluster(options);

            String clusterId = result.clusterId().get(TIMEOUT_SECONDS, TimeUnit.SECONDS);
            int nodeCount = result.nodes().get(TIMEOUT_SECONDS, TimeUnit.SECONDS).size();

            return Health.up()
                    .withDetail("clusterId", clusterId)
                    .withDetail("nodeCount", nodeCount)
                    .build();
        } catch (Exception e) {
            log.warn("Kafka health check failed: {}", e.getMessage());
            return Health.down()
                    .withDetail("error", e.getMessage())
                    .build();
        }
    }
}
