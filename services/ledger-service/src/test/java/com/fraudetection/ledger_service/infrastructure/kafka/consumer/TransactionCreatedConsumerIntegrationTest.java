package com.fraudetection.ledger_service.infrastructure.kafka.consumer;

import com.fraudetection.ledger_service.BaseIntegrationTest;
import com.fraudetection.ledger_service.domain.model.LedgerEntry;
import com.fraudetection.ledger_service.shared.dto.event.KafkaEventEnvelope;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

class TransactionCreatedConsumerIntegrationTest extends BaseIntegrationTest {

    @Test
    @DisplayName("Should consume transaction.created event and ensure idempotency via Redis")
    void shouldConsumeTransactionCreatedAndBeIdempotent() throws Exception {
        UUID eventId = UUID.randomUUID();
        UUID transactionId = UUID.randomUUID();
        UUID sourceAccountId = UUID.randomUUID();
        UUID destinationAccountId = UUID.randomUUID();

        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("transactionId", transactionId.toString());
        payload.put("sourceAccountId", sourceAccountId.toString());
        payload.put("destinationAccountId", destinationAccountId.toString());
        payload.put("amount", 1500.00);
        payload.put("type", "PIX");
        payload.put("deviceId", "device-abc123");
        payload.put("ipAddress", "192.168.1.1");
        payload.put("idempotencyKey", UUID.randomUUID().toString());
        payload.put("createdAt", "2026-08-24T10:30:00Z");

        KafkaEventEnvelope envelope = new KafkaEventEnvelope(
                eventId,
                "TRANSACTION_CREATED",
                Instant.parse("2026-08-24T10:30:00Z"),
                payload
        );

        String json = objectMapper.writeValueAsString(envelope);

        // 1. Publish event to Kafka
        testKafkaTemplate.send(TransactionCreatedConsumer.TOPIC, transactionId.toString(), json).get(5, TimeUnit.SECONDS);

        // 2. Await persistence and verify Redis idempotency key
        await().atMost(10, TimeUnit.SECONDS).untilAsserted(() -> {
            Query query = new Query(Criteria.where("transactionId").is(transactionId));
            List<LedgerEntry> entries = mongoTemplate.find(query, LedgerEntry.class);
            assertThat(entries).hasSize(1);

            LedgerEntry entry = entries.getFirst();
            assertThat(entry.getId()).isNotNull();
            assertThat(entry.getEventType()).isEqualTo("TRANSACTION_CREATED");
            assertThat(entry.getKafkaTopic()).isEqualTo("transaction.created");
            assertThat(entry.getEventPayload()).isNotNull();
            assertThat(entry.getEventPayload().get("amount")).isEqualTo(1500.00);
            assertThat(entry.getEventPayload().get("sourceAccountId")).isEqualTo(sourceAccountId.toString());
            assertThat(entry.getEventPayload().get("destinationAccountId")).isEqualTo(destinationAccountId.toString());

            Boolean hasKey = redisTemplate.hasKey("processed:" + eventId);
            assertThat(hasKey).isTrue();
        });

        // 3. Publish DUPLICATE event to verify idempotency
        testKafkaTemplate.send(TransactionCreatedConsumer.TOPIC, transactionId.toString(), json).get(5, TimeUnit.SECONDS);

        // Give Kafka a moment to process the duplicate message
        Thread.sleep(1500);

        // 4. Verify MongoDB still has exactly 1 entry
        Query query = new Query(Criteria.where("transactionId").is(transactionId));
        List<LedgerEntry> entriesAfterDuplicate = mongoTemplate.find(query, LedgerEntry.class);
        assertThat(entriesAfterDuplicate).hasSize(1);
    }
}
