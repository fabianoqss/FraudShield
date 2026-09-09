package com.fraudetection.ledger_service.shared.dto.response;

import com.fraudetection.ledger_service.domain.model.LedgerEntry;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

public record LedgerEntryResponse(
        UUID id,
        UUID transactionId,
        String eventType,
        Map<String, Object> eventPayload,
        String kafkaTopic,
        long kafkaOffset,
        Instant recordedAt
) {
    public static LedgerEntryResponse from(LedgerEntry entry) {
        return new LedgerEntryResponse(
                entry.getId(),
                entry.getTransactionId(),
                entry.getEventType(),
                entry.getEventPayload(),
                entry.getKafkaTopic(),
                entry.getKafkaOffset(),
                entry.getRecordedAt()
        );
    }
}
