package com.fraudetection.ledger_service.api.controller;

import com.fraudetection.ledger_service.BaseIntegrationTest;
import com.fraudetection.ledger_service.domain.model.LedgerEntry;
import com.fraudetection.ledger_service.infrastructure.client.AccountServiceClient;
import com.fraudetection.ledger_service.shared.dto.exception.AccountAccessDeniedException;
import com.fraudetection.ledger_service.shared.dto.exception.AccountNotFoundException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doThrow;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@AutoConfigureMockMvc
class LedgerControllerTest extends BaseIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private AccountServiceClient accountServiceClient;

    @Test
    @DisplayName("Should return paginated ledger entries sorted by recordedAt DESC when user owns the account")
    void shouldReturnLedgerEntriesSortedDescWhenOwner() throws Exception {
        UUID requestingUserId = UUID.randomUUID();
        UUID accountId = UUID.randomUUID();
        UUID otherAccountId = UUID.randomUUID();

        // 1. Insert test entries
        Instant time1 = Instant.parse("2026-08-24T10:00:00Z");
        Instant time2 = Instant.parse("2026-08-24T10:30:00Z");
        Instant time3 = Instant.parse("2026-08-24T11:00:00Z");

        // Entry 1: sourceAccountId == accountId
        LedgerEntry entry1 = new LedgerEntry(
                UUID.randomUUID(),
                UUID.randomUUID(),
                "TRANSACTION_CREATED",
                Map.of("sourceAccountId", accountId.toString(), "amount", 100.0),
                "transaction.created",
                1L,
                time1
        );

        // Entry 2: destinationAccountId == accountId
        LedgerEntry entry2 = new LedgerEntry(
                UUID.randomUUID(),
                UUID.randomUUID(),
                "TRANSACTION_APPROVED",
                Map.of("destinationAccountId", accountId.toString(), "amount", 200.0),
                "transaction.approved",
                2L,
                time2
        );

        // Entry 3: sourceAccountId == accountId
        LedgerEntry entry3 = new LedgerEntry(
                UUID.randomUUID(),
                UUID.randomUUID(),
                "TRANSACTION_DENIED",
                Map.of("sourceAccountId", accountId.toString(), "reason", "HIGH_FRAUD_SCORE"),
                "transaction.denied",
                3L,
                time3
        );

        // Entry 4: Unrelated account (must NOT be returned)
        LedgerEntry unrelated = new LedgerEntry(
                UUID.randomUUID(),
                UUID.randomUUID(),
                "TRANSACTION_CREATED",
                Map.of("sourceAccountId", otherAccountId.toString()),
                "transaction.created",
                4L,
                time2
        );

        mongoTemplate.save(entry1);
        mongoTemplate.save(entry2);
        mongoTemplate.save(entry3);
        mongoTemplate.save(unrelated);

        doNothing().when(accountServiceClient).verifyAccountOwnership(accountId, requestingUserId);

        mockMvc.perform(get("/ledger/account/{id}", accountId)
                        .header("X-User-Id", requestingUserId.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements", is(3)))
                .andExpect(jsonPath("$.content", hasSize(3)))
                .andExpect(jsonPath("$.content[0].eventType", is("TRANSACTION_DENIED")))
                .andExpect(jsonPath("$.content[1].eventType", is("TRANSACTION_APPROVED")))
                .andExpect(jsonPath("$.content[2].eventType", is("TRANSACTION_CREATED")));
    }

    @Test
    @DisplayName("Should return paginated slice when page and size are specified")
    void shouldReturnPaginatedSlice() throws Exception {
        UUID requestingUserId = UUID.randomUUID();
        UUID accountId = UUID.randomUUID();

        for (int i = 0; i < 5; i++) {
            LedgerEntry entry = new LedgerEntry(
                    UUID.randomUUID(),
                    UUID.randomUUID(),
                    "TRANSACTION_CREATED",
                    Map.of("sourceAccountId", accountId.toString()),
                    "transaction.created",
                    (long) i,
                    Instant.parse("2026-08-24T10:0" + i + ":00Z")
            );
            mongoTemplate.save(entry);
        }

        doNothing().when(accountServiceClient).verifyAccountOwnership(accountId, requestingUserId);

        mockMvc.perform(get("/ledger/account/{id}", accountId)
                        .param("page", "0")
                        .param("size", "2")
                        .header("X-User-Id", requestingUserId.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements", is(5)))
                .andExpect(jsonPath("$.totalPages", is(3)))
                .andExpect(jsonPath("$.pageNumber", is(0)))
                .andExpect(jsonPath("$.pageSize", is(2)))
                .andExpect(jsonPath("$.content", hasSize(2)));
    }

    @Test
    @DisplayName("Should return 403 Forbidden when user is not the account owner")
    void shouldReturn403WhenAccessDenied() throws Exception {
        UUID requestingUserId = UUID.randomUUID();
        UUID accountId = UUID.randomUUID();

        doThrow(new AccountAccessDeniedException(accountId))
                .when(accountServiceClient).verifyAccountOwnership(accountId, requestingUserId);

        mockMvc.perform(get("/ledger/account/{id}", accountId)
                        .header("X-User-Id", requestingUserId.toString()))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.status", is(403)))
                .andExpect(jsonPath("$.error", is("Forbidden")))
                .andExpect(jsonPath("$.message", is("Access denied for account: " + accountId)))
                .andExpect(jsonPath("$.path", is("/ledger/account/" + accountId)));
    }

    @Test
    @DisplayName("Should return 404 Not Found when account does not exist")
    void shouldReturn404WhenAccountNotFound() throws Exception {
        UUID requestingUserId = UUID.randomUUID();
        UUID accountId = UUID.randomUUID();

        doThrow(new AccountNotFoundException(accountId))
                .when(accountServiceClient).verifyAccountOwnership(accountId, requestingUserId);

        mockMvc.perform(get("/ledger/account/{id}", accountId)
                        .header("X-User-Id", requestingUserId.toString()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status", is(404)))
                .andExpect(jsonPath("$.error", is("Not Found")))
                .andExpect(jsonPath("$.message", is("Account not found: " + accountId)))
                .andExpect(jsonPath("$.path", is("/ledger/account/" + accountId)));
    }

    @Test
    @DisplayName("Should return 401 Unauthorized when X-User-Id header is missing")
    void shouldReturn401WhenHeaderMissing() throws Exception {
        UUID accountId = UUID.randomUUID();

        mockMvc.perform(get("/ledger/account/{id}", accountId))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.status", is(401)))
                .andExpect(jsonPath("$.error", is("Unauthorized")))
                .andExpect(jsonPath("$.message", is("Missing or invalid X-User-Id header")))
                .andExpect(jsonPath("$.path", is("/ledger/account/" + accountId)));
    }
}
