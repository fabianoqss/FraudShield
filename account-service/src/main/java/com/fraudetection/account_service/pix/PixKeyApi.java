package com.fraudetection.account_service.pix;

import com.fraudetection.account_service.pix.dto.PixKeyLookupRequest;
import com.fraudetection.account_service.pix.dto.PixKeyLookupResponse;
import com.fraudetection.account_service.pix.dto.PixKeyResponse;
import com.fraudetection.account_service.pix.dto.RegisterPixKeyRequest;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.headers.Header;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;

import java.util.List;
import java.util.UUID;

/**
 * OpenAPI documentation of {@link PixKeyController}. Documentation only: routing, validation and behavior stay
 * in the controller.
 */
@Tag(name = "PIX keys", description = "Register PIX keys on the caller's accounts and look up a recipient "
        + "before a transfer.")
public interface PixKeyApi {

    @Operation(summary = "Register a PIX key on an account",
            description = "The value is not sent: CPF and e-mail come from the caller's profile, a random key is "
                    + "generated. Each key is unique across the system.")
    @ApiResponse(responseCode = "201", description = "Key registered")
    @ApiResponse(responseCode = "400", description = "Validation failed, or the account id is not a UUID")
    @ApiResponse(responseCode = "401", description = "Missing or invalid bearer token")
    @ApiResponse(responseCode = "403", description = "The account belongs to another user")
    @ApiResponse(responseCode = "404", description = "Account not found")
    @ApiResponse(responseCode = "409", description = "This key is already registered")
    @ApiResponse(responseCode = "422", description = "The account is not active, or it reached its key limit")
    @ApiResponse(responseCode = "503", description = "auth-service is unavailable to read the caller's CPF or e-mail")
    ResponseEntity<PixKeyResponse> register(@Parameter(description = "Account id") UUID accountId,
                                            RegisterPixKeyRequest request,
                                            @Parameter(hidden = true) Authentication authentication);

    @Operation(summary = "List an account's PIX keys")
    @ApiResponse(responseCode = "200", description = "The account's keys, oldest first")
    @ApiResponse(responseCode = "400", description = "The account id is not a UUID")
    @ApiResponse(responseCode = "401", description = "Missing or invalid bearer token")
    @ApiResponse(responseCode = "403", description = "The account belongs to another user")
    @ApiResponse(responseCode = "404", description = "Account not found")
    ResponseEntity<List<PixKeyResponse>> list(@Parameter(description = "Account id") UUID accountId,
                                              @Parameter(hidden = true) Authentication authentication);

    @Operation(summary = "Remove a PIX key from an account")
    @ApiResponse(responseCode = "204", description = "Key removed")
    @ApiResponse(responseCode = "400", description = "An id is not a UUID")
    @ApiResponse(responseCode = "401", description = "Missing or invalid bearer token")
    @ApiResponse(responseCode = "403", description = "The account belongs to another user")
    @ApiResponse(responseCode = "404", description = "Account or key not found")
    ResponseEntity<Void> delete(@Parameter(description = "Account id") UUID accountId,
                                @Parameter(description = "PIX key id") UUID keyId,
                                @Parameter(hidden = true) Authentication authentication);

    @Operation(summary = "Look up the recipient of a PIX key",
            description = "Returns the recipient's name and masked CPF so the caller can confirm, plus a "
                    + "`lookupId` that `POST /transactions` accepts in place of an account id. The `lookupId` "
                    + "expires at `expiresAt` and only works for the caller. Lookups are rate limited per user.")
    @ApiResponse(responseCode = "200", description = "Recipient found")
    @ApiResponse(responseCode = "400", description = "Validation failed, or the key is not a CPF (11 digits), "
            + "an e-mail or a random key (UUID)")
    @ApiResponse(responseCode = "401", description = "Missing or invalid bearer token")
    @ApiResponse(responseCode = "404", description = "No account has this PIX key")
    @ApiResponse(responseCode = "429", description = "Too many lookups; retry after the given seconds",
            headers = @Header(name = "Retry-After", description = "Seconds until lookups are allowed again",
                    schema = @Schema(type = "integer")))
    @ApiResponse(responseCode = "503", description = "Lookups are temporarily unavailable")
    ResponseEntity<PixKeyLookupResponse> lookup(PixKeyLookupRequest request,
                                                @Parameter(hidden = true) Authentication authentication);
}
