package com.fraudetection.fraud_detection_service.kafka.producers;

import com.fraudetection.fraud_detection_service.dto.event.KafkaEventEnvelope;
import com.fraudetection.fraud_detection_service.dto.event.TransactionDeniedPayload;
import com.fraudetection.fraud_detection_service.entities.FraudAnalysis;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.UUID;

@Slf4j
@Component
@RequiredArgsConstructor
public class TransactionDeniedProducer {

    private static final String TOPIC = "transaction.denied";

    private final KafkaTemplate<String, Object> kafkaTemplate;

    public void publish(FraudAnalysis fraudAnalysis) {
        TransactionDeniedPayload payload = new TransactionDeniedPayload(
                fraudAnalysis.getTransactionId(),
                fraudAnalysis.getSourceAccountId(),
                fraudAnalysis.getDestinationAccountId(),
                fraudAnalysis.getAmount(),
                fraudAnalysis.getFraudScore(),
                fraudAnalysis.getReason(),
                fraudAnalysis.getModelVersion(),
                fraudAnalysis.getAnalyzedAt()
        );

        KafkaEventEnvelope envelope = new KafkaEventEnvelope(
                UUID.randomUUID(),
                "TRANSACTION_DENIED",
                Instant.now(),
                payload
        );

        kafkaTemplate.send(TOPIC, fraudAnalysis.getTransactionId().toString(), envelope);
        log.info("Published TRANSACTION_DENIED for transaction {}", fraudAnalysis.getTransactionId());
    }
}
