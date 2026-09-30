package com.fraudetection.transaction_service.dto.event;

import com.fraudetection.transaction_service.enums.PaymentType;

import java.math.BigDecimal;
import java.time.Instant;
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
        Instant createdAt
) {
}
