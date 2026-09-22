package com.fraudetection.transaction_service.controllers;

import com.fraudetection.transaction_service.security.SecurityConfig;
import com.fraudetection.transaction_service.services.TransactionService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.BadJwtException;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.UUID;

import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import org.springframework.http.MediaType;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(TransactionController.class)
@Import(SecurityConfig.class)
class TransactionControllerSecurityTests {

    private static final UUID ID = UUID.randomUUID();
    private static final String PATH = "/transactions/" + ID;

    @Autowired
    MockMvc mockMvc;

    @MockitoBean
    JwtDecoder jwtDecoder;

    @MockitoBean
    TransactionService transactionService;

    @Test
    void rejectsMissingToken() throws Exception {
        mockMvc.perform(get(PATH))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.message").value("Missing or invalid bearer token"));
    }

    @Test
    void rejectsForgedUserHeader() throws Exception {
        mockMvc.perform(get(PATH).header("X-User-Id", UUID.randomUUID()))
                .andExpect(status().isUnauthorized());
        verifyNoInteractions(transactionService);
    }

    @Test
    void acceptsJwt() throws Exception {
        mockMvc.perform(get(PATH).with(jwt().jwt(token -> token.subject(UUID.randomUUID().toString()))))
                .andExpect(status().isOk());
        verify(transactionService).getTransaction(ID);
    }

    @Test
    void createsTransactionWithJwt() throws Exception {
        mockMvc.perform(post("/transactions")
                        .with(jwt().jwt(token -> token.subject(UUID.randomUUID().toString())))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "sourceAccountId": "%s",
                                  "destinationAccountId": "%s",
                                  "amount": 10,
                                  "type": "PIX",
                                  "idempotencyKey": "security-test"
                                }
                                """.formatted(ID, UUID.randomUUID())))
                .andExpect(status().isCreated());
        verify(transactionService).createTransaction(any());
    }

    @Test
    void rejectsInvalidBearerTokenWithJsonError() throws Exception {
        when(jwtDecoder.decode("invalid")).thenThrow(new BadJwtException("Invalid token"));
        mockMvc.perform(get(PATH).header("Authorization", "Bearer invalid"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.message").value("Missing or invalid bearer token"));
    }
}
