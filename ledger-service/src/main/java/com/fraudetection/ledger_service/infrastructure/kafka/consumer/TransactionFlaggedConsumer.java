package com.fraudetection.ledger_service.infrastructure.kafka.consumer;

import com.fraudetection.ledger_service.application.service.LedgerService;
import com.fraudetection.ledger_service.infrastructure.redis.IdempotencyService;
import com.fraudetection.ledger_service.shared.dto.event.KafkaEventEnvelope;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.KafkaHeaders;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class TransactionFlaggedConsumer {

    public static final String TOPIC = "transaction.flagged";

    private final IdempotencyService idempotencyService;
    private final LedgerService ledgerService;

    @KafkaListener(topics = TOPIC, groupId = "ledger-service")
    public void handle(KafkaEventEnvelope envelope, @Header(KafkaHeaders.OFFSET) long offset) {
        if (idempotencyService.isProcessed(envelope.getEventId())) {
            log.info("Event already processed: {}", envelope.getEventId());
            return;
        }

        ledgerService.record(TOPIC, envelope, offset);

        idempotencyService.markProcessed(envelope.getEventId());
    }
}
