package com.fraudetection.transaction_service.dto.event;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.util.UUID;

@JsonIgnoreProperties(ignoreUnknown = true)
public record TransactionOutcomePayload(
        UUID transactionId
) {
}
