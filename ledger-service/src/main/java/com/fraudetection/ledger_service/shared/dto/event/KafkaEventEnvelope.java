package com.fraudetection.ledger_service.shared.dto.event;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

public record KafkaEventEnvelope(
        UUID eventId,
        String eventType,
        Instant timestamp,
        Map<String, Object> payload
) {
    public UUID getEventId() {
        return eventId;
    }
}
