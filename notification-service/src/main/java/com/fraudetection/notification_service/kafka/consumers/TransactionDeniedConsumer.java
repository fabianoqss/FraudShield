package com.fraudetection.notification_service.kafka.consumers;

import tools.jackson.databind.ObjectMapper;
import com.fraudetection.notification_service.dto.NotificationRequest;
import com.fraudetection.notification_service.dto.event.KafkaEventEnvelope;
import com.fraudetection.notification_service.dto.event.TransactionDeniedPayload;
import com.fraudetection.notification_service.services.IdempotencyService;
import com.fraudetection.notification_service.services.NotificationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class TransactionDeniedConsumer {

    private static final String TOPIC = "transaction.denied";

    private final IdempotencyService idempotencyService;
    private final NotificationService notificationService;
    private final ObjectMapper objectMapper;

    @KafkaListener(topics = TOPIC, groupId = "notification-service")
    public void handle(KafkaEventEnvelope envelope) {
        if (idempotencyService.alreadyProcessed(envelope.eventId())) {
            log.info("Consumer received event with id {} already processed", envelope.eventId());
            return;
        }

        TransactionDeniedPayload payload =
                objectMapper.convertValue(envelope.payload(), TransactionDeniedPayload.class);

        notificationService.notify(new NotificationRequest(
                payload.transactionId(),
                payload.sourceAccountId(),
                payload.destinationAccountId(),
                payload.amount(),
                NotificationRequest.Status.DENIED,
                payload.reason(),
                payload.analyzedAt()
        ));

        idempotencyService.markProcessed(envelope.eventId());
    }
}
