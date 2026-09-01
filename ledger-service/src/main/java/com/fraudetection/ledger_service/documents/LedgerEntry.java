package com.fraudetection.ledger_service.documents;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

@Document(collection = "ledger_entries")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class LedgerEntry {

    @Id
    private UUID id;
    private UUID transactionId;
    private String eventType;
    private Map<String, Object> eventPayload;
    private String kafkaTopic;
    private long kafkaOffset;
    private Instant recordedAt;
}
