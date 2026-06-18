package com.aisa.chat.web;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.aisa.chat.domain.ChatMessage;
import com.aisa.chat.domain.Conversation;
import com.aisa.chat.domain.MessageRole;
import com.aisa.chat.service.ChatService;
import com.aisa.chat.service.InvalidMessageException;
import jakarta.persistence.EntityNotFoundException;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

/**
 * Web-layer tests for {@link ChatController} (Requirements 5.1, 5.2, 5.9).
 * Validates that valid messages are accepted, empty/over-limit content is rejected
 * with field-level errors preserving the user's input, and that userId and timestamp
 * are associated correctly.
 */
@WebMvcTest(ChatController.class)
class ChatControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private ChatService chatService;

    private static final UUID USER_ID = UUID.randomUUID();
    private static final UUID PROJECT_ID = UUID.randomUUID();
    private static final UUID CONVERSATION_ID = UUID.randomUUID();

    // --- POST /api/chat/conversations tests ---

    @Test
    void createConversation_returnsCreated() throws Exception {
        Conversation conv = new Conversation(USER_ID, PROJECT_ID);
        when(chatService.createConversation(eq(USER_ID), eq(PROJECT_ID))).thenReturn(conv);

        mockMvc.perform(post("/api/chat/conversations")
                        .header("X-User-Id", USER_ID.toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"projectId":"%s"}
                                """.formatted(PROJECT_ID)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.userId").value(USER_ID.toString()))
                .andExpect(jsonPath("$.projectId").value(PROJECT_ID.toString()))
                .andExpect(jsonPath("$.createdAt").exists())
                .andExpect(jsonPath("$.updatedAt").exists());
    }

    // --- POST /api/chat/{conversationId}/messages tests ---

    @Test
    void validMessageIsPersistedAndReturns201() throws Exception {
        Conversation conv = new Conversation(USER_ID, PROJECT_ID);
        ChatMessage saved = new ChatMessage(conv, MessageRole.USER, "Hello world", USER_ID);

        when(chatService.sendMessage(eq(CONVERSATION_ID), eq(USER_ID), eq("Hello world")))
                .thenReturn(saved);

        mockMvc.perform(post("/api/chat/conversations/{conversationId}/messages", CONVERSATION_ID)
                        .header("X-User-Id", USER_ID.toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"content":"Hello world"}
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.role").value("USER"))
                .andExpect(jsonPath("$.content").value("Hello world"))
                .andExpect(jsonPath("$.userId").value(USER_ID.toString()));
    }

    @Test
    void emptyContentIsRejectedWithFieldError() throws Exception {
        when(chatService.sendMessage(eq(CONVERSATION_ID), eq(USER_ID), eq("")))
                .thenThrow(new InvalidMessageException("content must be between 1 and 10000 characters", ""));

        mockMvc.perform(post("/api/chat/conversations/{conversationId}/messages", CONVERSATION_ID)
                        .header("X-User-Id", USER_ID.toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"content":""}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.fieldErrors[0].field").value("content"))
                .andExpect(jsonPath("$.fieldErrors[0].rejectedValue").value(""));
    }

    @Test
    void overLimitContentIsRejectedWithInputPreserved() throws Exception {
        String overLimit = "x".repeat(10001);

        when(chatService.sendMessage(eq(CONVERSATION_ID), eq(USER_ID), eq(overLimit)))
                .thenThrow(new InvalidMessageException("content must be between 1 and 10000 characters", overLimit));

        mockMvc.perform(post("/api/chat/conversations/{conversationId}/messages", CONVERSATION_ID)
                        .header("X-User-Id", USER_ID.toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"content":"%s"}
                                """.formatted(overLimit)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.fieldErrors[0].field").value("content"))
                .andExpect(jsonPath("$.fieldErrors[0].rejectedValue").value(overLimit));
    }

    @Test
    void nullContentIsRejectedWithFieldError() throws Exception {
        mockMvc.perform(post("/api/chat/conversations/{conversationId}/messages", CONVERSATION_ID)
                        .header("X-User-Id", USER_ID.toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.fieldErrors[?(@.field=='content')]").exists());
    }

    @Test
    void missingUserIdHeaderReturnsBadRequest() throws Exception {
        mockMvc.perform(post("/api/chat/conversations/{conversationId}/messages", CONVERSATION_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"content":"Hello"}
                                """))
                .andExpect(status().isBadRequest());
    }

    @Test
    void exactlyAtMaxLengthIsAccepted() throws Exception {
        String maxContent = "a".repeat(10000);
        Conversation conv = new Conversation(USER_ID, PROJECT_ID);
        ChatMessage saved = new ChatMessage(conv, MessageRole.USER, maxContent, USER_ID);

        when(chatService.sendMessage(any(), any(), any())).thenReturn(saved);

        mockMvc.perform(post("/api/chat/conversations/{conversationId}/messages", CONVERSATION_ID)
                        .header("X-User-Id", USER_ID.toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"content":"%s"}
                                """.formatted(maxContent)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.role").value("USER"));
    }

    @Test
    void validMessageHasTimestampInResponse() throws Exception {
        Conversation conv = new Conversation(USER_ID, PROJECT_ID);
        ChatMessage saved = new ChatMessage(conv, MessageRole.USER, "Test timestamp", USER_ID);

        when(chatService.sendMessage(eq(CONVERSATION_ID), eq(USER_ID), eq("Test timestamp")))
                .thenReturn(saved);

        mockMvc.perform(post("/api/chat/conversations/{conversationId}/messages", CONVERSATION_ID)
                        .header("X-User-Id", USER_ID.toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"content":"Test timestamp"}
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.createdAt").exists())
                .andExpect(jsonPath("$.userId").value(USER_ID.toString()));
    }

    @Test
    void conversationNotFoundReturns404() throws Exception {
        UUID unknownConv = UUID.randomUUID();
        when(chatService.sendMessage(eq(unknownConv), eq(USER_ID), eq("Hello")))
                .thenThrow(new EntityNotFoundException("Conversation not found: " + unknownConv));

        mockMvc.perform(post("/api/chat/conversations/{conversationId}/messages", unknownConv)
                        .header("X-User-Id", USER_ID.toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"content":"Hello"}
                                """))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("NOT_FOUND"));
    }

    // --- POST /api/chat/messages tests (body-based conversationId) ---

    @Test
    void messagesEndpoint_validMessageIsPersistedAndReturns201() throws Exception {
        Conversation conv = new Conversation(USER_ID, PROJECT_ID);
        ChatMessage saved = new ChatMessage(conv, MessageRole.USER, "Hello flat", USER_ID);

        when(chatService.sendMessage(eq(CONVERSATION_ID), eq(USER_ID), eq("Hello flat")))
                .thenReturn(saved);

        mockMvc.perform(post("/api/chat/messages")
                        .header("X-User-Id", USER_ID.toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"conversationId":"%s","content":"Hello flat"}
                                """.formatted(CONVERSATION_ID)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.role").value("USER"))
                .andExpect(jsonPath("$.content").value("Hello flat"))
                .andExpect(jsonPath("$.userId").value(USER_ID.toString()))
                .andExpect(jsonPath("$.createdAt").exists());
    }

    @Test
    void messagesEndpoint_emptyContentIsRejected() throws Exception {
        mockMvc.perform(post("/api/chat/messages")
                        .header("X-User-Id", USER_ID.toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"conversationId":"%s","content":""}
                                """.formatted(CONVERSATION_ID)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.fieldErrors[?(@.field=='content')]").exists());
    }

    @Test
    void messagesEndpoint_overLimitContentIsRejected() throws Exception {
        String overLimit = "x".repeat(10001);

        mockMvc.perform(post("/api/chat/messages")
                        .header("X-User-Id", USER_ID.toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"conversationId":"%s","content":"%s"}
                                """.formatted(CONVERSATION_ID, overLimit)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.fieldErrors[?(@.field=='content')]").exists());
    }

    @Test
    void messagesEndpoint_missingConversationIdIsRejected() throws Exception {
        mockMvc.perform(post("/api/chat/messages")
                        .header("X-User-Id", USER_ID.toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"content":"Hello"}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.fieldErrors[?(@.field=='conversationId')]").exists());
    }
}
