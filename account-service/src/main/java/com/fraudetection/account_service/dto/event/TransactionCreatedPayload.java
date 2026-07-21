package com.fraudetection.account_service.dto.event;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.math.BigDecimal;
import java.util.UUID;

@JsonIgnoreProperties(ignoreUnknown = true)
public record TransactionCreatedPayload(
        UUID transactionId,
        UUID sourceAccountId,
        BigDecimal amount
) {
}
