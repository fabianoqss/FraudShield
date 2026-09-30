package com.fraudetection.account_service.controllers;

import com.fraudetection.account_service.dto.request.CreateAccountRequest;
import com.fraudetection.account_service.dto.request.PixDepositRequest;
import com.fraudetection.account_service.dto.response.AccountResponse;
import com.fraudetection.account_service.dto.response.BalanceResponse;
import com.fraudetection.account_service.dto.response.DepositResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirements;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;

import java.util.List;
import java.util.UUID;

/**
 * OpenAPI documentation of {@link AccountController}. Documentation only: routing, validation and behavior stay
 * in the controller.
 */
@Tag(name = "Accounts", description = "The caller's accounts, balances and the simulated incoming PIX deposit.")
public interface AccountApi {

    @Operation(summary = "List the caller's accounts")
    @ApiResponse(responseCode = "200", description = "The caller's accounts, possibly empty")
    @ApiResponse(responseCode = "401", description = "Missing or invalid bearer token")
    ResponseEntity<List<AccountResponse>> list(@Parameter(hidden = true) Authentication authentication);

    @Operation(summary = "Open an account for the caller",
            description = "`ownerId` must be the caller's own user id.")
    @ApiResponse(responseCode = "201", description = "Account created")
    @ApiResponse(responseCode = "400", description = "Validation failed")
    @ApiResponse(responseCode = "401", description = "Missing or invalid bearer token")
    @ApiResponse(responseCode = "403", description = "`ownerId` is not the caller")
    ResponseEntity<AccountResponse> create(CreateAccountRequest request,
                                           @Parameter(hidden = true) Authentication authentication);

    @Operation(summary = "Get an account's balance")
    @ApiResponse(responseCode = "200", description = "Current, locked and available balance")
    @ApiResponse(responseCode = "400", description = "The account id is not a UUID")
    @ApiResponse(responseCode = "401", description = "Missing or invalid bearer token")
    @ApiResponse(responseCode = "403", description = "The account belongs to another user")
    @ApiResponse(responseCode = "404", description = "Account not found")
    ResponseEntity<BalanceResponse> getBalance(@Parameter(description = "Account id") UUID id,
                                               @Parameter(hidden = true) Authentication authentication);

    @SecurityRequirements // public: simulates an incoming PIX transfer
    @Operation(summary = "Deposit into an account by PIX key",
            description = "Simulates a PIX transfer arriving from another bank. Public, no token needed. "
                    + "Each deposit is capped at 10000.00. The response only carries the receiver's name and the "
                    + "amount.")
    @ApiResponse(responseCode = "200", description = "Amount credited")
    @ApiResponse(responseCode = "400", description = "Validation failed (including more than 2 decimals or more "
            + "than 10000.00), or the key is not a CPF (11 digits), an e-mail or a random key (UUID)")
    @ApiResponse(responseCode = "404", description = "No account has this PIX key")
    ResponseEntity<DepositResponse> deposit(PixDepositRequest request);
}
