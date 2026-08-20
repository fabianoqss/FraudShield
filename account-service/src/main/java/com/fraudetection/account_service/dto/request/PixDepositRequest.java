package com.fraudetection.account_service.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

import java.math.BigDecimal;

public record PixDepositRequest(
        @NotBlank(message = "PIX key is required")
        String pixKey,

        @NotNull(message = "Amount is required")
        @Positive(message = "Amount must be positive")
        BigDecimal amount
) {
    public boolean isEmailKey() {
        return pixKey.contains("@");
    }
}
