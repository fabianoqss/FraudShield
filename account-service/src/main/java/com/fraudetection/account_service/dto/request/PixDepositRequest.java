package com.fraudetection.account_service.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

import java.math.BigDecimal;

public record PixDepositRequest(
        @Schema(description = "PIX key that receives the deposit: a CPF (11 digits, dots and dash optional), "
                + "an e-mail or a random key (UUID).", example = "ana.souza@example.com")
        @NotBlank(message = "PIX key is required")
        String pixKey,
        @Schema(description = "Amount in BRL, up to 2 decimals and at most 10000.00 per deposit.", example = "150.00")
        @NotNull(message = "Amount is required")
        @Positive(message = "Amount must be positive")
        @Digits(integer = 15, fraction = 2, message = "Amount must have at most 2 decimal places")
        // The deposit is public and creates money (it simulates another bank), so each one is capped.
        @DecimalMax(value = "10000.00", message = "Amount must be at most 10000.00 per deposit")
        BigDecimal amount
) {
}
