package com.fraudetection.fraud_detection_service.dto.event;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fraudetection.fraud_detection_service.enums.PaymentType;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

@JsonIgnoreProperties(ignoreUnknown = true)
public record TransactionCreatedPayload(
        UUID transactionId,
        UUID sourceAccountId,
        UUID destinationAccountId,
        BigDecimal amount,
        PaymentType type,
        String deviceId,
        String ipAddress,
        LocalDateTime createdAt
) {
}
