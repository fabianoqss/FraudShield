package com.fraudetection.transaction_service.dto.event;

import com.fraudetection.transaction_service.enums.PaymentType;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

public record TransactionCreatedPayload(
        UUID transactionId,
        UUID sourceAccountId,
        UUID destinationAccountId,
        BigDecimal amount,
        PaymentType type,
        String deviceId,
        String ipAddress,
        String idempotencyKey,
        LocalDateTime createdAt
) {
}
