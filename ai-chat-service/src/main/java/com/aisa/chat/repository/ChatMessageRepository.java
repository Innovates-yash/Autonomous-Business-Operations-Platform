package com.aisa.chat.repository;

import com.aisa.chat.domain.ChatMessage;
import java.util.List;
import java.util.UUID;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/**
 * Spring Data JPA repository for {@link ChatMessage} entities.
 */
public interface ChatMessageRepository extends JpaRepository<ChatMessage, UUID> {

    List<ChatMessage> findTop20ByConversationIdOrderByCreatedAtDesc(UUID conversationId);

    /**
     * Retrieves the most recent messages for a project (across all conversations for
     * that project), ordered by creation time descending. Used to rebuild the Redis
     * context window on cache miss (Requirement 5.3).
     *
     * @param projectId the project identifier
     * @param pageable  page request limiting the result size
     * @return messages ordered newest-first
     */
    @Query("SELECT m FROM ChatMessage m JOIN m.conversation c " +
            "WHERE c.projectId = :projectId ORDER BY m.createdAt DESC")
    List<ChatMessage> findByConversationProjectIdOrderByCreatedAtDesc(
            @Param("projectId") UUID projectId, Pageable pageable);
}
