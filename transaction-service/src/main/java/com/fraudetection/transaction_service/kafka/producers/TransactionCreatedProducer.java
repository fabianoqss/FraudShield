package com.fraudetection.transaction_service.kafka.producers;

import com.fraudetection.transaction_service.dto.event.KafkaEventEnvelope;
import com.fraudetection.transaction_service.dto.event.TransactionCreatedPayload;
import com.fraudetection.transaction_service.entities.Transaction;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.UUID;

@Slf4j
@Component
@RequiredArgsConstructor
public class TransactionCreatedProducer {

    private static final String TOPIC = "transaction.created";

    private final KafkaTemplate<String, Object> kafkaTemplate;

    public void publish(Transaction transaction) {
        TransactionCreatedPayload payload = new TransactionCreatedPayload(
                transaction.getId(),
                transaction.getSourceAccountId(),
                transaction.getDestinationAccountId(),
                transaction.getAmount(),
                transaction.getType(),
                transaction.getDeviceId(),
                transaction.getIpAddress(),
                transaction.getIdempotencyKey(),
                transaction.getCreatedAt()
        );

        KafkaEventEnvelope envelope = new KafkaEventEnvelope(
                UUID.randomUUID(),
                "TRANSACTION_CREATED",
                Instant.now(),
                payload
        );

        kafkaTemplate.send(TOPIC, transaction.getId().toString(), envelope);
        log.info("Published TRANSACTION_CREATED for transaction {}", transaction.getId());
    }
}
