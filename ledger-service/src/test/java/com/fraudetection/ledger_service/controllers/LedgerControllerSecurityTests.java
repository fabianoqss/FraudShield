package com.fraudetection.ledger_service.controllers;

import com.fraudetection.ledger_service.documents.LedgerEntry;
import com.fraudetection.ledger_service.security.SecurityConfig;
import com.fraudetection.ledger_service.security.TokenTypeAuthoritiesConverter;
import com.fraudetection.ledger_service.clients.AccountServiceClient;
import com.fraudetection.ledger_service.services.LedgerQueryService;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.BadJwtException;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(LedgerController.class)
@Import(SecurityConfig.class)
class LedgerControllerSecurityTests {

    private static final UUID ID = UUID.randomUUID();
    private static final String PATH = "/ledger/account/" + ID;

    @Autowired
    MockMvc mockMvc;

    @MockitoBean
    JwtDecoder jwtDecoder;

    @MockitoBean
    AccountServiceClient accountServiceClient;

    @MockitoBean
    LedgerQueryService ledgerQueryService;

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
        verifyNoInteractions(accountServiceClient, ledgerQueryService);
    }

    @Test
    void acceptsJwt() throws Exception {
        when(ledgerQueryService.getEntriesForAccount(ID, 0, 20)).thenReturn(Page.empty());
        mockMvc.perform(get(PATH).with(userJwt(UUID.randomUUID())))
                .andExpect(status().isOk());
        verify(accountServiceClient).verifyOwnership(ID);
    }

    @Test
    void rejectsInvalidBearerTokenWithJsonError() throws Exception {
        when(jwtDecoder.decode("invalid")).thenThrow(new BadJwtException("Invalid token"));
        mockMvc.perform(get(PATH).header("Authorization", "Bearer invalid"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.message").value("Missing or invalid bearer token"));
    }

    @Test
    void serviceTokenCannotCallUserRoutes() throws Exception {
        mockMvc.perform(get(PATH).with(jwt()
                        .jwt(token -> token.subject("account-service").claim("token_type", "service").claim("scope", "users:lookup"))
                        .authorities(new TokenTypeAuthoritiesConverter())))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.message").value("Access denied"));
    }


    @Test
    void recipientGetsOnlyStatementFields() throws Exception {
        stubEntry(UUID.randomUUID());
        mockMvc.perform(get(PATH).with(userJwt(UUID.randomUUID())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.entries[0].direction").value("INCOMING"))
                .andExpect(jsonPath("$.entries[0].amount").value(12.34))
                .andExpect(jsonPath("$.entries[0].reason").doesNotExist())
                .andExpect(jsonPath("$.entries[0].length()").value(6))
                .andExpect(jsonPath("$.entries[0].eventPayload").doesNotExist())
                .andExpect(jsonPath("$.entries[0].fraudScore").doesNotExist())
                .andExpect(jsonPath("$.entries[0].ipAddress").doesNotExist())
                .andExpect(jsonPath("$.entries[0].deviceId").doesNotExist())
                .andExpect(jsonPath("$.entries[0].idempotencyKey").doesNotExist())
                .andExpect(jsonPath("$.entries[0].sourceAccountId").doesNotExist())
                .andExpect(jsonPath("$.entries[0].destinationAccountId").doesNotExist())
                .andExpect(jsonPath("$.entries[0].kafkaTopic").doesNotExist())
                .andExpect(jsonPath("$.entries[0].kafkaOffset").doesNotExist());
    }

    @Test
    void senderGetsDeniedReason() throws Exception {
        stubEntry(ID);
        mockMvc.perform(get(PATH).with(userJwt(UUID.randomUUID())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.entries[0].direction").value("OUTGOING"))
                .andExpect(jsonPath("$.entries[0].reason").value("Risk detected"))
                .andExpect(jsonPath("$.entries[0].length()").value(7));
    }

    @Test
    void invalidAccountIdReturnsBadRequest() throws Exception {
        mockMvc.perform(get("/ledger/account/not-a-uuid").with(userJwt(UUID.randomUUID())))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Invalid value for parameter: id"));
        verifyNoInteractions(accountServiceClient, ledgerQueryService);
    }

    private void stubEntry(UUID source) {
        var payload = Map.<String, Object>of(
                "sourceAccountId", source.toString(), "destinationAccountId", (source.equals(ID) ? UUID.randomUUID() : ID).toString(),
                "amount", 12.34, "reason", "Risk detected", "fraudScore", 0.99,
                "ipAddress", "8.8.8.8", "deviceId", "secret-device", "idempotencyKey", "secret-key");
        var entry = new LedgerEntry(
                UUID.randomUUID(), UUID.randomUUID(), "TRANSACTION_DENIED", payload,
                "transaction.denied", 42, Instant.now());
        when(ledgerQueryService.getEntriesForAccount(ID, 0, 20))
                .thenReturn(new PageImpl<>(List.of(entry)));
    }

    private static SecurityMockMvcRequestPostProcessors.JwtRequestPostProcessor userJwt(UUID userId) {
        return jwt()
                .jwt(token -> token.subject(userId.toString()).claim("token_type", "user"))
                .authorities(new TokenTypeAuthoritiesConverter());
    }
}
