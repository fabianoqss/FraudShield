package com.fraudetection.ledger_service.application.service;

import com.fraudetection.ledger_service.domain.model.LedgerEntry;
import com.fraudetection.ledger_service.domain.repository.LedgerEntryRepository;
import com.fraudetection.ledger_service.shared.dto.event.KafkaEventEnvelope;
import com.fraudetection.ledger_service.shared.dto.response.LedgerEntryResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class LedgerService {

    private final LedgerEntryRepository ledgerEntryRepository;

    public void record(String kafkaTopic, KafkaEventEnvelope envelope, long kafkaOffset) {
        Object rawTxId = envelope.payload() != null ? envelope.payload().get("transactionId") : null;
        UUID transactionId = rawTxId != null ? UUID.fromString(rawTxId.toString()) : null;

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
        log.info("Recorded ledger entry {} for transaction {} from topic {} (offset {})",
                entry.getId(), transactionId, kafkaTopic, kafkaOffset);
    }

    public Page<LedgerEntryResponse> getAccountLedger(UUID accountId, int page, int size) {
        Pageable pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "recordedAt"));
        Page<LedgerEntry> entries = ledgerEntryRepository.findByAccountId(accountId.toString(), pageable);
        return entries.map(LedgerEntryResponse::from);
    }
}
