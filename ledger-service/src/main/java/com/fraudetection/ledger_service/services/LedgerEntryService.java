package com.fraudetection.ledger_service.services;

import com.fraudetection.ledger_service.documents.LedgerEntry;
import com.fraudetection.ledger_service.dto.event.KafkaEventEnvelope;
import com.fraudetection.ledger_service.repositories.LedgerEntryRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class LedgerEntryService {

    private final LedgerEntryRepository ledgerEntryRepository;

    public void record(String kafkaTopic, KafkaEventEnvelope envelope, long kafkaOffset) {
        UUID transactionId = UUID.fromString((String) envelope.payload().get("transactionId"));

        LedgerEntry entry = new LedgerEntry(
                UUID.randomUUID(),
                transactionId,
                envelope.eventType(),
                envelope.payload(),
                kafkaTopic,
                kafkaOffset,
                Instant.now()
        );
        ledgerEntryRepository.save(entry);

        log.info("Recorded ledger entry for transaction {} from topic {} (offset {})", transactionId, kafkaTopic, kafkaOffset);
    }
}
