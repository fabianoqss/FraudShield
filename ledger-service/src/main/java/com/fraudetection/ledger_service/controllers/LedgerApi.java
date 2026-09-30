package com.fraudetection.ledger_service.controllers;

import com.fraudetection.ledger_service.dto.response.LedgerEntryPageResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;

import java.util.UUID;

/**
 * OpenAPI documentation of {@link LedgerController}. Documentation only: routing and behavior stay in the
 * controller.
 */
@Tag(name = "Ledger", description = "The audit trail of transfer events per account.")
public interface LedgerApi {

    @Operation(summary = "Get an account's ledger",
            description = "Every transfer event involving the account, newest first: `TRANSACTION_CREATED`, "
                    + "`TRANSACTION_APPROVED`, `TRANSACTION_DENIED` or `TRANSACTION_FLAGGED`. The sender sees the reason for "
                    + "DENIED/FLAGGED; the recipient sees only the event, direction and amount. Deposits are not recorded. "
                    + "`page` starts at 0; `size` is clamped to 1..100.")
    @ApiResponse(responseCode = "200", description = "One page of ledger entries")
    @ApiResponse(responseCode = "400", description = "Invalid account id or pagination parameter")
    @ApiResponse(responseCode = "401", description = "Missing or invalid bearer token")
    @ApiResponse(responseCode = "403", description = "The account belongs to another user")
    @ApiResponse(responseCode = "404", description = "Account not found")
    @ApiResponse(responseCode = "503", description = "account-service is unavailable")
    ResponseEntity<LedgerEntryPageResponse> getAccountLedger(@Parameter(description = "Account id") UUID id,
                                                             @Parameter(description = "Page number, from 0") int page,
                                                             @Parameter(description = "Page size, 1..100") int size);
}
