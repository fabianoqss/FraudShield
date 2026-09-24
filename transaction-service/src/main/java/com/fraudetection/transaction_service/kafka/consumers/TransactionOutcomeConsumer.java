package com.fraudetection.transaction_service.kafka.consumers;

import tools.jackson.databind.ObjectMapper;
import com.fraudetection.transaction_service.dto.event.KafkaEventEnvelope;
import com.fraudetection.transaction_service.dto.event.TransactionOutcomePayload;
import com.fraudetection.transaction_service.enums.PaymentStatus;
import com.fraudetection.transaction_service.services.TransactionService;
import lombok.RequiredArgsConstructor;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class TransactionOutcomeConsumer {

    private final TransactionService transactionService;
    private final ObjectMapper objectMapper;

    @KafkaListener(topics = "transaction.approved", groupId = "transaction-service")
    public void handleApproved(KafkaEventEnvelope envelope) {
        apply(envelope, PaymentStatus.APPROVED);
    }

    @KafkaListener(topics = "transaction.denied", groupId = "transaction-service")
    public void handleDenied(KafkaEventEnvelope envelope) {
        apply(envelope, PaymentStatus.DENIED);
    }

    @KafkaListener(topics = "transaction.flagged", groupId = "transaction-service")
    public void handleFlagged(KafkaEventEnvelope envelope) {
        apply(envelope, PaymentStatus.FLAGGED);
    }

    private void apply(KafkaEventEnvelope envelope, PaymentStatus outcome) {
        TransactionOutcomePayload payload = objectMapper.convertValue(envelope.payload(), TransactionOutcomePayload.class);
        transactionService.applyOutcome(payload.transactionId(), outcome);
    }
}
