package com.fraudetection.fraud_detection_service.dto.event;

import java.time.Instant;
import java.util.UUID;

public record KafkaEventEnvelope(
        UUID eventId,
        String eventType,
        Instant timestamp,
        Object payload
) {
}
