package com.fraudetection.account_service.dto.event;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

public record KafkaEventEnvelope(
        UUID eventId,
        String eventType,
        Instant timestamp,
        Map<String, Object> payload
) {
}
