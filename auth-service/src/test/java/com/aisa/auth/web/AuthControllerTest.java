package com.aisa.auth.web;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.aisa.auth.service.AuthenticationService;
import com.aisa.auth.service.DuplicateAccountException;
import com.aisa.auth.service.InvalidCredentialsException;
import com.aisa.auth.service.InvalidRefreshTokenException;
import com.aisa.auth.service.RegistrationService;
import com.aisa.auth.web.dto.RegistrationResponse;
import com.aisa.auth.web.dto.TokenResponse;
import com.aisa.commons.error.ErrorCodes;
import java.time.Instant;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

/**
 * Web-layer tests for {@link AuthController} and {@link AuthExceptionHandler}:
 * registration (Requirements 1.1, 1.2, 1.12), login token issuance and the uniform
 * invalid-credentials error (Requirements 1.3, 1.9), refresh rejection
 * (Requirement 1.7), and logout invalidation (Requirement 1.10).
 */
@WebMvcTest(AuthController.class)
class AuthControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private RegistrationService registrationService;

    @MockBean
    private AuthenticationService authenticationService;

    @Test
    void registersValidRequestAndReturns201() throws Exception {
        when(registrationService.register(any())).thenReturn(
                new RegistrationResponse(1L, "user@example.com", "GUEST", Instant.now()));

        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"user@example.com","password":"Abcdefg1!xyz"}
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(1))
                .andExpect(jsonPath("$.email").value("user@example.com"))
                .andExpect(jsonPath("$.role").value("GUEST"));
    }

    @Test
    void rejectsShortAndSimplePasswordWithFieldError() throws Exception {
        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"user@example.com","password":"short"}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(ErrorCodes.VALIDATION_ERROR))
                .andExpect(jsonPath("$.fieldErrors[?(@.field=='password')]").exists());
    }

    @Test
    void rejectsMalformedEmailWithFieldError() throws Exception {
        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"not-an-email","password":"Abcdefg1!xyz"}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(ErrorCodes.VALIDATION_ERROR))
                .andExpect(jsonPath("$.fieldErrors[?(@.field=='email')]").exists());
    }

    @Test
    void rejectsDuplicateEmailWith409() throws Exception {
        when(registrationService.register(any()))
                .thenThrow(new DuplicateAccountException("An account with this email already exists"));

        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"dupe@example.com","password":"Abcdefg1!xyz"}
                                """))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value(ErrorCodes.DUPLICATE_ACCOUNT));
    }

 