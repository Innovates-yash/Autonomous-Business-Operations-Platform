package com.aisa.chat.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.aisa.chat.domain.ChatMessage;
import com.aisa.chat.domain.Conversation;
import com.aisa.chat.domain.MessageRole;
import com.aisa.chat.repository.ChatMessageRepository;
import com.aisa.chat.repository.ConversationRepository;
import jakarta.persistence.EntityNotFoundException;
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
 * Validates that messages are persisted with correct user association and UTC timestamp,
 * empty/over-limit content is rejected preserving the user's input, and conversations
 * are created correctly.
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
    private static final UUID CONVERSATION_ID = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        chatService = new ChatService(conversationRepository, chatMessageRepository);
    }

    @Test
    void createConversation_persistsWithUserAndProject() {
        Conversation newConv = new Conversation(USER_ID, PROJECT_ID);
        when(conversationRepository.save(any(Conversation.class))).thenReturn(newConv);

        Conversation result = chatService.createConversation(USER_ID, PROJECT_ID);

        assertThat(result).isNotNull();
        assertThat(result.getUserId()).isEqualTo(USER_ID);
        assertThat(result.getProjectId()).isEqualTo(PROJECT_ID);
        assertThat(result.getCreatedAt()).isNotNull();

        ArgumentCaptor<Conversation> captor = ArgumentCaptor.forClass(Conversation.class);
        verify(conversationRepository).save(captor.capture());
        assertThat(captor.getValue().getUserId()).isEqualTo(USER_ID);
        assertThat(captor.getValue().getProjectId()).isEqualTo(PROJECT_ID);
    }

    @Test
    void sendMessage_validContentIsSaved() {
        Conversation conv = new Conversation(USER_ID, PROJECT_ID);
        when(conversationRepository.findById(CONVERSATION_ID)).thenReturn(Optional.of(conv));

        ChatMessage expectedMessage = new ChatMessage(conv, MessageRole.USER, "Hello world", USER_ID);
        when(chatMessageRepository.save(any(ChatMessage.class))).thenReturn(expectedMessage);

        ChatMessage result = chatService.sendMessage(CONVERSATION_ID, USER_ID, "Hello world");

        assertThat(result).isNotNull();
        assertThat(result.getContent()).isEqualTo("Hello world");
        assertThat(result.getUserId()).isEqualTo(USER_ID);
        assertThat(result.getRole()).isEqualTo(MessageRole.USER);
        assertThat(result.getCreatedAt()).isNotNull();
    }

    @Test
    void sendMessage_associatesUserIdAndUtcTimestamp() {
        Conversation conv = new Conversation(USER_ID, PROJECT_ID);
        when(conversationRepository.findById(CONVERSATION_ID)).thenReturn(Optional.of(conv));
        when(chatMessageRepository.save(any(ChatMessage.class))).thenAnswer(inv -> inv.getArgument(0));

        ChatMessage result = chatService.sendMessage(CONVERSATION_ID, USER_ID, "Test message");

        assertThat(result.getUserId()).isEqualTo(USER_ID);
        assertThat(result.getCreatedAt()).isNotNull();
        assertThat(result.getRole()).isEqualTo(MessageRole.USER);
    }

    @Test
    void sendMessage_emptyContentIsRejectedPreservingInput() {
        Conversation conv = new Conversation(USER_ID, PROJECT_ID);
        when(conversationRepository.findById(CONVERSATION_ID)).thenReturn(Optional.of(conv));

        assertThatThrownBy(() -> chatService.sendMessage(CONVERSATION_ID, USER_ID, ""))
                .isInstanceOf(InvalidMessageException.class)
                .satisfies(ex -> {
                    InvalidMessageException ime = (InvalidMessageException) ex;
                    assertThat(ime.getRejectedContent()).isEqualTo("");
                    assertThat(ime.getMessage()).contains("1 and 10000");
                });
    }

    @Test
    void sendMessage_nullContentIsRejectedPreservingInput() {
        Conversation conv = new Conversation(USER_ID, PROJECT_ID);
        when(conversationRepository.findById(CONVERSATION_ID)).thenReturn(Optional.of(conv));

        assertThatThrownBy(() -> chatService.sendMessage(CONVERSATION_ID, USER_ID, null))
                .isInstanceOf(InvalidMessageException.class)
                .satisfies(ex -> {
                    InvalidMessageException ime = (InvalidMessageException) ex;
                    assertThat(ime.getRejectedContent()).isNull();
                });
    }

    @Test
    void sendMessage_overLimitContentIsRejectedPreservingInput() {
        Conversation conv = new Conversation(USER_ID, PROJECT_ID);
        when(conversationRepository.findById(CONVERSATION_ID)).thenReturn(Optional.of(conv));

        String overLimit = "x".repeat(10001);

        assertThatThrownBy(() -> chatService.sendMessage(CONVERSATION_ID, USER_ID, overLimit))
                .isInstanceOf(InvalidMessageException.class)
                .satisfies(ex -> {
                    InvalidMessageException ime = (InvalidMessageException) ex;
                    assertThat(ime.getRejectedContent()).isEqualTo(overLimit);
                    assertThat(ime.getMessage()).contains("1 and 10000");
                });
    }

    @Test
    void sendMessage_exactlyAtMaxLengthIsAccepted() {
        Conversation conv = new Conversation(USER_ID, PROJECT_ID);
        when(conversationRepository.findById(CONVERSATION_ID)).thenReturn(Optional.of(conv));
        when(chatMessageRepository.save(any(ChatMessage.class))).thenAnswer(inv -> inv.getArgument(0));

        String maxContent = "a".repeat(10000);
        ChatMessage result = chatService.sendMessage(CONVERSATION_ID, USER_ID, maxContent);

        assertThat(result.getContent()).isEqualTo(maxContent);
    }

    @Test
    void sendMessage_conversationNotFoundThrowsEntityNotFound() {
        when(conversationRepository.findById(CONVERSATION_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> chatService.sendMessage(CONVERSATION_ID, USER_ID, "Hello"))
                .isInstanceOf(EntityNotFoundException.class)
                .hasMessageContaining(CONVERSATION_ID.toString());
    }
}
