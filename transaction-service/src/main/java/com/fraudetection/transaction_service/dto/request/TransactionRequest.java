package com.fraudetection.transaction_service.dto.request;

import com.fraudetection.transaction_service.enums.PaymentType;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

import java.math.BigDecimal;
import java.util.UUID;

public record TransactionRequest(

        @Schema(description = "The caller's account the money leaves.")
        @NotNull UUID sourceAccountId,

        @Schema(description = "From `POST /accounts/pix-keys/lookup`, after the user confirmed the recipient. "
                + "Valid for 5 minutes, only for the user who made the lookup.")
        @NotNull UUID lookupId,

        @Schema(description = "Amount in BRL, up to 2 decimals.", example = "100.00")
        @NotNull @Positive BigDecimal amount,

        @NotNull PaymentType type,

        @Schema(description = "Optional fraud signal. A browser client generates a random id once and keeps it "
                + "in localStorage.")
        String deviceId,

        @Schema(description = "Optional fraud signal; a browser client sends null.")
        String ipAddress,

        @Schema(description = "A new UUID per transfer the user submits; reuse it only to retry the same "
                + "submission (e.g. after a network error).")
        @NotBlank String idempotencyKey

) {
}
