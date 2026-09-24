package com.fraudetection.transaction_service.controllers;

import com.fraudetection.transaction_service.security.SecurityConfig;
import com.fraudetection.transaction_service.security.TokenTypeAuthoritiesConverter;
import com.fraudetection.transaction_service.dto.response.TransactionPageResponse;
import com.fraudetection.transaction_service.services.TransactionService;
import com.fraudetection.transaction_service.services.exceptions.AccountAccessDeniedException;
import com.fraudetection.transaction_service.services.exceptions.AccountNotFoundException;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.BadJwtException;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;
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
        mockMvc.perform(get(PATH).with(userJwt(UUID.randomUUID())))
                .andExpect(status().isOk());
        verify(transactionService).getTransaction(ID);
    }

    @Test
    void createsTransactionWithJwt() throws Exception {
        mockMvc.perform(post("/transactions")
                        .with(userJwt(UUID.randomUUID()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "sourceAccountId": "%s",
                                  "lookupId": "%s",
                                  "amount": 10,
                                  "type": "PIX",
                                  "idempotencyKey": "security-test"
                                }
                                """.formatted(ID, UUID.randomUUID())))
                .andExpect(status().isCreated());
        verify(transactionService).createTransaction(any());
    }

    @Test
    void listsTransactionsOfAccount() throws Exception {
        when(transactionService.listTransactions(ID, 1, 5))
                .thenReturn(new TransactionPageResponse(List.of(), 1, 5, 0, 0));

        mockMvc.perform(get("/transactions").param("accountId", ID.toString()).param("page", "1").param("size", "5")
                        .with(userJwt(UUID.randomUUID())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.transactions").isArray())
                .andExpect(jsonPath("$.page").value(1));
    }

    @Test
    void listUsesDefaultPaging() throws Exception {
        mockMvc.perform(get("/transactions").param("accountId", ID.toString()).with(userJwt(UUID.randomUUID())))
                .andExpect(status().isOk());
        verify(transactionService).listTransactions(ID, 0, 20);
    }

    @Test
    void listWithoutAccountIdIsBadRequest() throws Exception {
        mockMvc.perform(get("/transactions").with(userJwt(UUID.randomUUID())))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Missing required parameter: accountId"));
        verifyNoInteractions(transactionService);
    }

    @Test
    void listWithMalformedAccountIdIsBadRequest() throws Exception {
        mockMvc.perform(get("/transactions").param("accountId", "not-a-uuid").with(userJwt(UUID.randomUUID())))
                .andExpect(status().isBadRequest());
        verifyNoInteractions(transactionService);
    }

    @Test
    void listOfAnotherUsersAccountIsForbidden() throws Exception {
        when(transactionService.listTransactions(ID, 0, 20)).thenThrow(new AccountAccessDeniedException(ID));

        mockMvc.perform(get("/transactions").param("accountId", ID.toString()).with(userJwt(UUID.randomUUID())))
                .andExpect(status().isForbidden());
    }

    @Test
    void listOfUnknownAccountIsNotFound() throws Exception {
        when(transactionService.listTransactions(ID, 0, 20)).thenThrow(new AccountNotFoundException(ID));

        mockMvc.perform(get("/transactions").param("accountId", ID.toString()).with(userJwt(UUID.randomUUID())))
                .andExpect(status().isNotFound());
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
        verifyNoInteractions(transactionService);
    }


    private static SecurityMockMvcRequestPostProcessors.JwtRequestPostProcessor userJwt(UUID userId) {
        return jwt()
                .jwt(token -> token.subject(userId.toString()).claim("token_type", "user"))
                .authorities(new TokenTypeAuthoritiesConverter());
    }
}
