package com.aisa.chat.repository;

import com.aisa.chat.domain.ChatMessage;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * Spring Data JPA repository for {@link ChatMessage} entities.
 */
public interface ChatMessageRepository extends JpaRepository<ChatMessage, UUID> {

    List<ChatMessage> findTop20ByConversationIdOrderByCreatedAtDesc(UUID conversationId);
}
