package com.fraudetection.notification_service.kafka.consumers;

import com.fraudetection.notification_service.dto.NotificationRequest;
import com.fraudetection.notification_service.dto.event.KafkaEventEnvelope;
import com.fraudetection.notification_service.dto.event.TransactionDeniedPayload;
import com.fraudetection.notification_service.services.NotificationService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.test.context.EmbeddedKafka;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

import static org.awaitility.Awaitility.await;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

@SpringBootTest
@EmbeddedKafka(partitions = 1, topics = {"transaction.denied", "transaction.denied-dlt"},
        bootstrapServersProperty = "spring.kafka.bootstrap-servers")
@Testcontainers
class TransactionDeniedConsumerTest {

    private static final String TOPIC = "transaction.denied";

    @Container
    static final GenericContainer<?> redis = new GenericContainer<>("redis:7-alpine").withExposedPorts(6379);

    @DynamicPropertySource
    static void redisProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.data.redis.host", redis::getHost);
        registry.add("spring.data.redis.port", () -> redis.getMappedPort(6379));
    }

    @Autowired
    private KafkaTemplate<String, Object> kafkaTemplate;

    @MockitoBean
    private NotificationService notificationService;

    @Test
    void consumirEventoTransactionDenied_shouldNotificar_whenEventoAindaNaoFoiProcessado() {
        UUID eventId = UUID.randomUUID();

        kafkaTemplate.send(TOPIC, eventId.toString(), buildEnvelope(eventId));

        await().atMost(Duration.ofSeconds(10))
                .untilAsserted(() -> verify(notificationService, times(1)).notify(any(NotificationRequest.class)));
    }

    @Test
    void consumirEventoTransactionDenied_shouldIgnorarProcessamento_whenEventoJaFoiProcessado() {
        UUID eventId = UUID.randomUUID();
        KafkaEventEnvelope envelope = buildEnvelope(eventId);

        kafkaTemplate.send(TOPIC, eventId.toString(), envelope);
        await().atMost(Duration.ofSeconds(10))
                .untilAsserted(() -> verify(notificationService, times(1)).notify(any(NotificationRequest.class)));

        kafkaTemplate.send(TOPIC, eventId.toString(), envelope);
        await().pollDelay(Duration.ofSeconds(2)).atMost(Duration.ofSeconds(10))
                .untilAsserted(() -> verify(notificationService, times(1)).notify(any(NotificationRequest.class)));
    }

    private KafkaEventEnvelope buildEnvelope(UUID eventId) {
        TransactionDeniedPayload payload = new TransactionDeniedPayload(
                UUID.randomUUID(),
                UUID.randomUUID(),
                UUID.randomUUID(),
                new BigDecimal("500.00"),
                new BigDecimal("0.92"),
                "HIGH_FRAUD_SCORE",
                "v1.0.0",
                Instant.now()
        );
        return new KafkaEventEnvelope(eventId, "TRANSACTION_DENIED", Instant.now(), payload);
    }
}
