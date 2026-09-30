package com.fraudetection.transaction_service.dto.response;

import com.fraudetection.transaction_service.enums.PaymentStatus;
import com.fraudetection.transaction_service.enums.PaymentType;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record TransactionResponse(
        UUID id,
        UUID sourceAccountId,
        UUID destinationAccountId,
        BigDecimal amount,
        PaymentType type,
        PaymentStatus status,
        Instant createdAt

) {
}
