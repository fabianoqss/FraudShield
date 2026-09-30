package com.fraudetection.ledger_service.dto.response;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fraudetection.ledger_service.documents.LedgerEntry;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record LedgerEntryResponse(
        UUID id,
        UUID transactionId,
        String eventType,
        String direction,
        BigDecimal amount,
        @JsonInclude(JsonInclude.Include.NON_NULL) String reason,
        Instant recordedAt
) {
    public static LedgerEntryResponse from(LedgerEntry entry, UUID accountId) {
        var payload = entry.getEventPayload();
        boolean outgoing = accountId.toString().equals(String.valueOf(payload.get("sourceAccountId")));
        Object amount = payload.get("amount");
        return new LedgerEntryResponse(
                entry.getId(),
                entry.getTransactionId(),
                entry.getEventType(),
                outgoing ? "OUTGOING" : "INCOMING",
                amount == null ? null : new BigDecimal(amount.toString()),
                outgoing && payload.get("reason") instanceof String reason ? reason : null,
                entry.getRecordedAt()
        );
    }
}
