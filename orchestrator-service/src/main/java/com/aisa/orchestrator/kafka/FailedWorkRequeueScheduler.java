package com.aisa.orchestrator.kafka;

import com.aisa.orchestrator.domain.FailedWorkItem;
import com.aisa.orchestrator.domain.FailedWorkItemStatus;
import com.aisa.orchestrator.repository.FailedWorkItemRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/**
 * Scheduler that re-queues failed Kafka submissions within 30 seconds (Requirement 26.6).
 *
 * <p>When Kafka is unavailable, the {@link KafkaSubmissionService} does not silently drop
 * data — it throws {@link KafkaSubmissionException} with the retained payload. The caller
 * persists a {@link FailedWorkItem} to MySQL. This scheduler polls for PENDING items
 * every 10 seconds and re-publishes them to Kafka.
 *
 * <p>Combined with a session.timeout.ms of 10s on the consumer side, total re-queue
 * latency stays under 30 seconds: up to 10s for the broker to detect a dead consumer
 * + up to 10s for this scheduler to pick up the item + up to 10s for the Kafka send.
 *
 * <p>Items that exhaust their retry budget are marked EXHAUSTED for manual intervention.
 */
@Component
public class FailedWorkRequeueScheduler {

    private static final Logger log = LoggerFactory.getLogger(FailedWorkRequeueScheduler.class);

    /** Timeout for each re-queue send attempt. */
    private static final Duration REQUEUE_SEND_TIMEOUT = Duration.ofSeconds(10);

    private final FailedWorkItemRepository failedWorkItemRepository;
    private final KafkaTemplate<String, Object> kafkaTemplate;

    public FailedWorkRequeueScheduler(FailedWorkItemRepository failedWorkItemRepository,
                                      KafkaTemplate<String, Object> kafkaTemplate) {
        this.failedWorkItemRepository = failedWorkItemRepository;
        this.kafkaTemplate = kafkaTemplate;
    }

    /**
     * Polls for PENDING failed work items every 10 seconds and re-submits them to Kafka.
     * Combined with session.timeout.ms=10s, this ensures re-queue within 30s (Req 26.6).
     */
    @Scheduled(fixedDelay = 10_000, initialDelay = 5_000)
    public void requeueFailedWork() {
        List<FailedWorkItem> pendingItems =
                failedWorkItemRepository.findByStatusOrderByCreatedAtAsc(FailedWorkItemStatus.PENDING);

        if (pendingItems.isEmpty()) {
            return;
        }

        log.info("Found {} PENDING failed work items to re-queue", pendingItems.size());

        for (FailedWorkItem item : pendingItems) {
            attemptRequeue(item);
        }
    }

    /**
     * Attempts to re-publish a single failed work item to Kafka.
     * On success, marks it COMPLETED. On failure, increments retry count
     * and marks EXHAUSTED if max retries reached.
     */
    void attemptRequeue(FailedWorkItem item) {
        item.setStatus(FailedWorkItemStatus.RETRYING);
        item.setLastAttemptAt(Instant.now());
        item.incrementRetryCount();
        failedWorkItemRepository.save(item);

        try {
            CompletableFuture<SendResult<String, Object>> future =
                    kafkaTemplate.send(item.getTopic(), item.getMessageKey(), item.getPayload());

            future.get(REQUEUE_SEND_TIMEOUT.toMillis(), TimeUnit.MILLISECONDS);

            item.setStatus(FailedWorkItemStatus.COMPLETED);
            failedWorkItemRepository.save(item);
            log.info("Successfully re-queued work item id={} to topic={}", item.getId(), item.getTopic());

        } catch (TimeoutException e) {
            handleRequeueFailure(item, "Re-queue send timed out after " + REQUEUE_SEND_TIMEOUT.toSeconds() + "s");
        } catch (Exception e) {
            Throwable cause = e.getCause() != null ? e.getCause() : e;
            handleRequeueFailure(item, "Re-queue failed: " + cause.getMessage());
        }
    }

    private void handleRequeueFailure(FailedWorkItem item, String errorMessage) {
        log.warn("Re-queue attempt {} failed for item id={}: {}",
                item.getRetryCount(), item.getId(), errorMessage);

        item.setErrorMessage(errorMessage);

        if (item.getRetryCount() >= item.getMaxRetries()) {
            item.setStatus(FailedWorkItemStatus.EXHAUSTED);
            log.error("Work item id={} exhausted all {} retries — requires manual intervention",
                    item.getId(), item.getMaxRetries());
        } else {
            item.setStatus(FailedWorkItemStatus.PENDING);
        }

        failedWorkItemRepository.save(item);
    }
}
