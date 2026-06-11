package com.aisa.chat.repository;

import com.aisa.chat.domain.Conversation;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * Spring Data JPA repository for {@link Conversation} entities.
 */
public interface ConversationRepository extends JpaRepository<Conversation, UUID> {

    Optional<Conversation> findByProjectId(UUID projectId);
}
