package com.fraudetection.ledger_service.kafka.consumers;

import com.fraudetection.ledger_service.dto.event.KafkaEventEnvelope;
import com.fraudetection.ledger_service.services.IdempotencyService;
import com.fraudetection.ledger_service.services.LedgerEntryService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.KafkaHeaders;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class TransactionApprovedConsumer {

    private static final String TOPIC = "transaction.approved";

    private final IdempotencyService idempotencyService;
    private final LedgerEntryService ledgerEntryService;

    @KafkaListener(topics = TOPIC, groupId = "ledger-service")
    public void handle(KafkaEventEnvelope envelope, @Header(KafkaHeaders.OFFSET) long offset) {
        if (idempotencyService.alreadyProcessed(envelope.eventId())) {
            log.info("Event already processed: {}", envelope.eventId());
            return;
        }

        ledgerEntryService.record(TOPIC, envelope, offset);

        idempotencyService.markProcessed(envelope.eventId());
    }
}
