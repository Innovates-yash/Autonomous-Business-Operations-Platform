package com.aisa.project.repository;

import com.aisa.project.domain.ClarifyingQuestion;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

/**
 * Repository for {@link ClarifyingQuestion} entities. Supports retrieval of
 * questions scoped to a Project and filtering by answer status for the
 * answer-incorporation workflow (Requirements 4.3, 4.4).
 */
@Repository
public interface ClarifyingQuestionRepository extends JpaRepository<ClarifyingQuestion, UUID> {

    /** All clarifying questions for a project, ordered by creation time. */
    List<ClarifyingQuestion> findByProjectIdOrderByCreatedAtAsc(UUID projectId);

    /** All unanswered questions for a project. */
    List<ClarifyingQuestion> findByProjectIdAndAnswerIsNullOrderByCreatedAtAsc(UUID projectId);

    /** All answered questions for a project. */
    List<ClarifyingQuestion> findByProjectIdAndAnswerIsNotNullOrderByCreatedAtAsc(UUID projectId);
}
