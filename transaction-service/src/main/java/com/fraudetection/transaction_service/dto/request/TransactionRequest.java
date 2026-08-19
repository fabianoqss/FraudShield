package com.fraudetection.transaction_service.dto.request;

import com.fraudetection.transaction_service.enums.PaymentType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

import java.math.BigDecimal;
import java.util.UUID;

public record TransactionRequest(

        @NotNull UUID sourceAccountId,

        @NotNull UUID destinationAccountId,

        @NotNull @Positive BigDecimal amount,

        @NotNull PaymentType type,

        String deviceId,

        String ipAddress,

        @NotBlank String idempotencyKey

) {
}
