package com.fraudetection.account_service.dto.event;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.util.UUID;

@JsonIgnoreProperties(ignoreUnknown = true)
public record TransactionDeniedPayload(
        UUID transactionId
) {
}
