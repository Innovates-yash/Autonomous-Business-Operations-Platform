package com.aisa.chat.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.aisa.chat.domain.ChatMessage;
import com.aisa.chat.domain.Conversation;
import com.aisa.chat.domain.MessageRole;
import com.aisa.chat.repository.ChatMessageRepository;
import com.aisa.chat.repository.ConversationRepository;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * Unit tests for {@link ChatService} (Requirements 5.1, 5.2, 5.9).
 * Validates that messages are persisted with correct user+project association,
 * conversations are created when none exist, and existing conversations are reused.
 */
@ExtendWith(MockitoExtension.class)
class ChatServiceTest {

    @Mock
    private ConversationRepository conversationRepository;

    @Mock
    private ChatMessageRepository chatMessageRepository;

    private ChatService chatService;

    private static final UUID USER_ID = UUID.randomUUID();
    private static final UUID PROJECT_ID = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        chatService = new ChatService(conversationRepository, chatMessageRepository);
    }

    @Test
    void sendMessage_createsConversationWhenNoneExists() {
        // Given no existing conversation for user+project
        when(conversationRepository.findByUserIdAndProjectId(USER_ID, PROJECT_ID))
                .thenReturn(Optional.empty());

        Conversation newConv = new Conversation(USER_ID, PROJECT_ID);
        when(conversationRepository.save(any(Conversation.class))).thenReturn(newConv);

        ChatMessage expectedMessage = new ChatMessage(newConv, MessageRole.USER, "Hello", USER_ID);
        when(chatMessageRepository.save(any(ChatMessage.class))).thenReturn(expectedMessage);

        // When
        ChatMessage result = chatService.sendMessage(USER_ID, PROJECT_ID, "Hello");

        // Then
        assertThat(result).isNotNull();
        assertThat(result.getContent()).isEqualTo("Hello");
        assertThat(result.getUserId()).isEqualTo(USER_ID);
        assertThat(result.getRole()).isEqualTo(MessageRole.USER);

        // Verify conversation was created
        ArgumentCaptor<Conversation> convCaptor = ArgumentCaptor.forClass(Conversation.class);
        verify(conversationRepository).save(convCaptor.capture());
        assertThat(convCaptor.getValue().getUserId()).isEqualTo(USER_ID);
        assertThat(convCaptor.getValue().getProjectId()).isEqualTo(PROJECT_ID);
    }

    @Test
    void sendMessage_reusesExistingConversation() {
        // Given an existing conversation for user+project
        Conversation existingConv = new Conversation(USER_ID, PROJECT_ID);
        when(conversationRepository.findByUserIdAndProjectId(USER_ID, PROJECT_ID))
                .thenReturn(Optional.of(existingConv));

        ChatMessage expectedMessage = new ChatMessage(existingConv, MessageRole.USER, "World", USER_ID);
        when(chatMessageRepository.save(any(ChatMessage.class))).thenReturn(expectedMessage);

        // When
        ChatMessage result = chatService.sendMessage(USER_ID, PROJECT_ID, "World");

        // Then
        assertThat(result).isNotNull();
        assertThat(result.getContent()).isEqualTo("World");

        // Verify no new conversation was created
        verify(conversationRepository, never()).save(any(Conversation.class));
    }

    @Test
    void sendMessage_associatesUserIdAndUtcTimestamp() {
        // Given
        Conversation conv = new Conversation(USER_ID, PROJECT_ID);
        when(conversationRepository.findByUserIdAndProjectId(USER_ID, PROJECT_ID))
                .thenReturn(Optional.of(conv));

        when(chatMessageRepository.save(any(ChatMessage.class))).thenAnswer(invocation -> invocation.getArgument(0));

        // When
        ChatMessage result = chatService.sendMessage(USER_ID, PROJECT_ID, "Test message");

        // Then
        assertThat(result.getUserId()).isEqualTo(USER_ID);
        assertThat(result.getCreatedAt()).isNotNull();
        assertThat(result.getRole()).isEqualTo(MessageRole.USER);
    }
}
