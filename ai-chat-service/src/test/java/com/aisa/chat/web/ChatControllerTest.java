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

    @Test
    void validMessageIsPersistedAndReturns201() throws Exception {
        Conversation conv = new Conversation(USER_ID, PROJECT_ID);
        ChatMessage saved = new ChatMessage(conv, MessageRole.USER, "Hello world", USER_ID);

        when(chatService.sendMessage(eq(USER_ID), eq(PROJECT_ID), eq("Hello world")))
                .thenReturn(saved);

        mockMvc.perform(post("/api/chat/messages")
                        .header("X-User-Id", USER_ID.toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"projectId":"%s","content":"Hello world"}
                                """.formatted(PROJECT_ID)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.role").value("USER"))
                .andExpect(jsonPath("$.content").value("Hello world"))
                .andExpect(jsonPath("$.userId").value(USER_ID.toString()));
    }

    @Test
    void emptyContentIsRejectedWithFieldError() throws Exception {
        mockMvc.perform(post("/api/chat/messages")
                        .header("X-User-Id", USER_ID.toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"projectId":"%s","content":""}
                                """.formatted(PROJECT_ID)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.fieldErrors[?(@.field=='content')]").exists())
                .andExpect(jsonPath("$.fieldErrors[?(@.field=='content')].rejectedValue[0]").value(""));
    }

    @Test
    void overLimitContentIsRejectedWithFieldError() throws Exception {
        String overLimit = "x".repeat(10001);

        mockMvc.perform(post("/api/chat/messages")
                        .header("X-User-Id", USER_ID.toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"projectId":"%s","content":"%s"}
                                """.formatted(PROJECT_ID, overLimit)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.fieldErrors[?(@.field=='content')]").exists())
                .andExpect(jsonPath("$.fieldErrors[?(@.field=='content')].rejectedValue").exists());
    }

    @Test
    void nullContentIsRejectedWithFieldError() throws Exception {
        mockMvc.perform(post("/api/chat/messages")
                        .header("X-User-Id", USER_ID.toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"projectId":"%s"}
                                """.formatted(PROJECT_ID)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.fieldErrors[?(@.field=='content')]").exists());
    }

    @Test
    void missingUserIdHeaderReturnsBadRequest() throws Exception {
        mockMvc.perform(post("/api/chat/messages")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"projectId":"%s","content":"Hello"}
                                """.formatted(PROJECT_ID)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void exactlyAtMaxLengthIsAccepted() throws Exception {
        String maxContent = "a".repeat(10000);
        Conversation conv = new Conversation(USER_ID, PROJECT_ID);
        ChatMessage saved = new ChatMessage(conv, MessageRole.USER, maxContent, USER_ID);

        when(chatService.sendMessage(any(), any(), any())).thenReturn(saved);

        mockMvc.perform(post("/api/chat/messages")
                        .header("X-User-Id", USER_ID.toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"projectId":"%s","content":"%s"}
                                """.formatted(PROJECT_ID, maxContent)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.role").value("USER"));
    }

    @Test
    void validMessageHasTimestampInResponse() throws Exception {
        Conversation conv = new Conversation(USER_ID, PROJECT_ID);
        ChatMessage saved = new ChatMessage(conv, MessageRole.USER, "Test timestamp", USER_ID);

        when(chatService.sendMessage(eq(USER_ID), eq(PROJECT_ID), eq("Test timestamp")))
                .thenReturn(saved);

        mockMvc.perform(post("/api/chat/messages")
                        .header("X-User-Id", USER_ID.toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"projectId":"%s","content":"Test timestamp"}
                                """.formatted(PROJECT_ID)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.createdAt").exists())
                .andExpect(jsonPath("$.userId").value(USER_ID.toString()));
    }
}
