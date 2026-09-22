package com.fraudetection.ledger_service.controllers;

import com.fraudetection.ledger_service.security.SecurityConfig;
import com.fraudetection.ledger_service.clients.AccountServiceClient;
import com.fraudetection.ledger_service.services.LedgerQueryService;
import org.springframework.data.domain.Page;
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
        mockMvc.perform(get(PATH).with(jwt().jwt(token -> token.subject(UUID.randomUUID().toString()))))
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
}
