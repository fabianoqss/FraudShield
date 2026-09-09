package com.fraudetection.fraud_detection_service.dto.event;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record TransactionFlaggedPayload(
        UUID transactionId,
        UUID sourceAccountId,
        UUID destinationAccountId,
        BigDecimal amount,
        BigDecimal fraudScore,
        String reason,
        String modelVersion,
        Instant analyzedAt
) {
}
