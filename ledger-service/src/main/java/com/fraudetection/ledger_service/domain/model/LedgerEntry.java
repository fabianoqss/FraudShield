package com.fraudetection.ledger_service.domain.model;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;
import org.springframework.data.mongodb.core.mapping.Field;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

@Document(collection = "ledger_entries")
@Getter
@NoArgsConstructor
@AllArgsConstructor
public class LedgerEntry {

    @Id
    private UUID id;

    @Field("transactionId")
    private UUID transactionId;

    @Field("eventType")
    private String eventType;

    @Field("eventPayload")
    private Map<String, Object> eventPayload;

    @Field("kafkaTopic")
    private String kafkaTopic;

    @Field("kafkaOffset")
    private long kafkaOffset;

    @Field("recordedAt")
    private Instant recordedAt;
}
