package com.aisa.orchestrator.repository;

import com.aisa.orchestrator.domain.FailedWorkItem;
import com.aisa.orchestrator.domain.FailedWorkItemStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

/**
 * Repository for {@link FailedWorkItem} entities.
 * Used by the re-queue scheduler to find pending items for re-submission (Req 26.6).
 */
@Repository
public interface FailedWorkItemRepository extends JpaRepository<FailedWorkItem, UUID> {

    /**
     * Finds all work items in the given status, ordered by creation time (FIFO).
     */
    List<FailedWorkItem> findByStatusOrderByCreatedAtAsc(FailedWorkItemStatus status);
}
