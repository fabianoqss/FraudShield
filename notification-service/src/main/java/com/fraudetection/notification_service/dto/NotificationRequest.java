package com.fraudetection.notification_service.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record NotificationRequest(
        UUID transactionId,
        UUID sourceAccountId,
        UUID destinationAccountId,
        BigDecimal amount,
        Status status,
        String reason,
        Instant analyzedAt
) {
    public enum Status {
        APPROVED, DENIED, FLAGGED
    }
}
