package com.fraudetection.transaction_service.kafka.consumers;

import tools.jackson.databind.json.JsonMapper;
import com.fraudetection.transaction_service.dto.event.KafkaEventEnvelope;
import com.fraudetection.transaction_service.enums.PaymentStatus;
import com.fraudetection.transaction_service.services.TransactionService;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

class TransactionOutcomeConsumerTest {

    private final TransactionService transactionService = mock(TransactionService.class);
    private final TransactionOutcomeConsumer consumer =
            new TransactionOutcomeConsumer(transactionService, JsonMapper.builder().build());

    private final UUID transactionId = UUID.randomUUID();

    @Test
    void approvedEventApprovesTransaction() {
        consumer.handleApproved(envelope("TRANSACTION_APPROVED"));

        verify(transactionService).applyOutcome(transactionId, PaymentStatus.APPROVED);
    }

    @Test
    void deniedEventDeniesTransaction() {
        consumer.handleDenied(envelope("TRANSACTION_DENIED"));

        verify(transactionService).applyOutcome(transactionId, PaymentStatus.DENIED);
    }

    @Test
    void flaggedEventFlagsTransaction() {
        consumer.handleFlagged(envelope("TRANSACTION_FLAGGED"));

        verify(transactionService).applyOutcome(transactionId, PaymentStatus.FLAGGED);
    }

    private KafkaEventEnvelope envelope(String eventType) {
        Map<String, Object> payload = Map.of(
                "transactionId", transactionId.toString(),
                "sourceAccountId", UUID.randomUUID().toString(),
                "fraudScore", 0.42,
                "reason", "score above threshold");
        return new KafkaEventEnvelope(UUID.randomUUID(), eventType, Instant.now(), payload);
    }
}
