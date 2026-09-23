package com.fraudetection.notification_service.dto.event;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record TransactionApprovedPayload(
        UUID transactionId,
        UUID sourceAccountId,
        UUID destinationAccountId,
        BigDecimal amount,
        BigDecimal fraudScore,
        String modelVersion,
        Instant analyzedAt
) {
}
