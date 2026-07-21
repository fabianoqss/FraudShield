package com.fraudetection.account_service.kafka.consumers;

import tools.jackson.databind.ObjectMapper;
import com.fraudetection.account_service.dto.event.KafkaEventEnvelope;
import com.fraudetection.account_service.dto.event.TransactionDeniedPayload;
import com.fraudetection.account_service.services.BalanceLockService;
import com.fraudetection.account_service.services.IdempotencyService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class TransactionDeniedConsumer {

    private final IdempotencyService idempotencyService;
    private final BalanceLockService balanceLockService;
    private final ObjectMapper objectMapper;

    @KafkaListener(topics = "transaction.denied", groupId = "account-service")
    public void handle(KafkaEventEnvelope envelope) {
        if (idempotencyService.alreadyProcessed(envelope.eventId())) {
            log.info("Event already processed: {}", envelope.eventId());
            return;
        }

        TransactionDeniedPayload payload = objectMapper.convertValue(envelope.payload(), TransactionDeniedPayload.class);
        balanceLockService.releaseOnDenial(payload);

        idempotencyService.markProcessed(envelope.eventId());
    }
}
