package com.fraudetection.notification_service.kafka.consumers;

import tools.jackson.databind.ObjectMapper;
import com.fraudetection.notification_service.dto.NotificationRequest;
import com.fraudetection.notification_service.dto.event.KafkaEventEnvelope;
import com.fraudetection.notification_service.dto.event.TransactionFlaggedPayload;
import com.fraudetection.notification_service.services.IdempotencyService;
import com.fraudetection.notification_service.services.NotificationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class TransactionFlaggedConsumer {

    private static final String TOPIC = "transaction.flagged";

    private final IdempotencyService idempotencyService;
    private final NotificationService notificationService;
    private final ObjectMapper objectMapper;

    @KafkaListener(topics = TOPIC, groupId = "notification-service")
    public void handle(KafkaEventEnvelope envelope) {
        if (idempotencyService.alreadyProcessed(envelope.eventId())) {
            log.info("Consumer received event with id {} already processed", envelope.eventId());
            return;
        }

        TransactionFlaggedPayload payload =
                objectMapper.convertValue(envelope.payload(), TransactionFlaggedPayload.class);

        notificationService.notify(new NotificationRequest(
                payload.transactionId(),
                payload.sourceAccountId(),
                payload.destinationAccountId(),
                payload.amount(),
                NotificationRequest.Status.FLAGGED,
                payload.reason(),
                payload.analyzedAt()
        ));

        idempotencyService.markProcessed(envelope.eventId());
    }
}
