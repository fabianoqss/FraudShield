package com.fraudetection.fraud_detection_service.kafka.consumers;

import tools.jackson.databind.ObjectMapper;
import com.fraudetection.fraud_detection_service.dto.event.KafkaEventEnvelope;
import com.fraudetection.fraud_detection_service.dto.event.TransactionCreatedPayload;
import com.fraudetection.fraud_detection_service.services.FraudAnalysisService;
import com.fraudetection.fraud_detection_service.services.IdempotencyService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class TransactionCreatedConsumer {

    private static final String TOPIC = "transaction.created";

    private final IdempotencyService idempotencyService;
    private final FraudAnalysisService fraudAnalysisService;
    private final ObjectMapper objectMapper;

    @KafkaListener(topics = TOPIC, groupId = "fraud-detection-service")
    public void handle(KafkaEventEnvelope envelope){
        if(idempotencyService.alreadyProcessed(envelope.eventId())){
            log.info("Consumer received event with id {} already processed", envelope.eventId());
            return;
        }

        TransactionCreatedPayload payload = objectMapper.convertValue(envelope.payload(), TransactionCreatedPayload.class);
        fraudAnalysisService.analyze(payload);

        idempotencyService.markProcessed(envelope.eventId());
    }
}
