package com.fraudetection.transaction_service.controllers;

import com.fraudetection.transaction_service.dto.request.TransactionRequest;
import com.fraudetection.transaction_service.dto.response.TransactionPageResponse;
import com.fraudetection.transaction_service.dto.response.TransactionResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.ResponseEntity;

import java.util.UUID;

/**
 * OpenAPI documentation of {@link TransactionController}. Documentation only: routing, validation and behavior
 * stay in the controller.
 */
@Tag(name = "Transactions", description = "Transfers between accounts and their fraud-analysis outcome.")
public interface TransactionApi {

    @Operation(summary = "Create a transfer",
            description = "The recipient comes from a PIX key lookup the user confirmed (`lookupId`). The amount is "
                    + "reserved on the source account and the transfer is created as `CREATED`; fraud analysis runs "
                    + "asynchronously, usually within a few seconds, and moves it to `APPROVED` (money moved), "
                    + "`DENIED` (reservation released) or `FLAGGED` (held for manual review, money stays reserved). "
                    + "The server obtains the client IP from the gateway connection. "
                    + "Poll `GET /transactions/{id}` (e.g. every 1-2 s, up to ~30 s) to show the outcome.")
    @ApiResponse(responseCode = "201", description = "Transfer created as `CREATED`")
    @ApiResponse(responseCode = "400", description = "Validation failed or malformed body")
    @ApiResponse(responseCode = "401", description = "Missing or invalid bearer token")
    @ApiResponse(responseCode = "403", description = "The source account belongs to another user")
    @ApiResponse(responseCode = "404", description = "Source account not found")
    @ApiResponse(responseCode = "409", description = "The idempotency key was already used")
    @ApiResponse(responseCode = "422", description = "Insufficient funds, PIX lookup expired or invalid (look the "
            + "key up again), or the destination is the source account")
    @ApiResponse(responseCode = "503", description = "account-service is unavailable")
    ResponseEntity<TransactionResponse> createRequest(TransactionRequest request,
                                                       @Parameter(hidden = true) HttpServletRequest servletRequest);

    @Operation(summary = "List an account's transfers",
            description = "Transfers where the account is the source or the destination, newest first. `page` "
                    + "starts at 0; `size` is clamped to 1..100.")
    @ApiResponse(responseCode = "200", description = "One page of transfers")
    @ApiResponse(responseCode = "400", description = "`accountId` is missing or not a UUID")
    @ApiResponse(responseCode = "401", description = "Missing or invalid bearer token")
    @ApiResponse(responseCode = "403", description = "The account belongs to another user")
    @ApiResponse(responseCode = "404", description = "Account not found")
    @ApiResponse(responseCode = "503", description = "account-service is unavailable")
    ResponseEntity<TransactionPageResponse> listRequest(@Parameter(description = "Account id") UUID accountId,
                                                        @Parameter(description = "Page number, from 0") int page,
                                                        @Parameter(description = "Page size, 1..100") int size);

    @Operation(summary = "Get a transfer and its current status",
            description = "Visible to the owners of the source and of the destination account.")
    @ApiResponse(responseCode = "200", description = "The transfer")
    @ApiResponse(responseCode = "400", description = "The id is not a UUID")
    @ApiResponse(responseCode = "401", description = "Missing or invalid bearer token")
    @ApiResponse(responseCode = "404", description = "Not found, or not visible to the caller")
    ResponseEntity<TransactionResponse> getRequest(@Parameter(description = "Transaction id") UUID id);
}
