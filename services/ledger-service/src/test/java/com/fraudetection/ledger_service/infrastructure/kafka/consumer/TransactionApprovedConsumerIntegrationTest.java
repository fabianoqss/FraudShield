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

class TransactionApprovedConsumerIntegrationTest extends BaseIntegrationTest {

    @Test
    @DisplayName("Should consume transaction.approved event and ensure idempotency via Redis")
    void shouldConsumeTransactionApprovedAndBeIdempotent() throws Exception {
        UUID eventId = UUID.randomUUID();
        UUID transactionId = UUID.randomUUID();
        UUID sourceAccountId = UUID.randomUUID();
        UUID destinationAccountId = UUID.randomUUID();

        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("transactionId", transactionId.toString());
        payload.put("sourceAccountId", sourceAccountId.toString());
        payload.put("destinationAccountId", destinationAccountId.toString());
        payload.put("amount", 1500.00);
        payload.put("fraudScore", 0.05);
        payload.put("modelVersion", "v1.0.0");
        payload.put("analyzedAt", "2026-08-24T10:30:01Z");

        KafkaEventEnvelope envelope = new KafkaEventEnvelope(
                eventId,
                "TRANSACTION_APPROVED",
                Instant.parse("2026-08-24T10:30:01Z"),
                payload
        );

        String json = objectMapper.writeValueAsString(envelope);

        // 1. Publish event to Kafka
        testKafkaTemplate.send(TransactionApprovedConsumer.TOPIC, transactionId.toString(), json).get(5, TimeUnit.SECONDS);

        // 2. Await persistence and verify Redis idempotency key
        await().atMost(10, TimeUnit.SECONDS).untilAsserted(() -> {
            Query query = new Query(Criteria.where("transactionId").is(transactionId));
            List<LedgerEntry> entries = mongoTemplate.find(query, LedgerEntry.class);
            assertThat(entries).hasSize(1);

            LedgerEntry entry = entries.getFirst();
            assertThat(entry.getId()).isNotNull();
            assertThat(entry.getEventType()).isEqualTo("TRANSACTION_APPROVED");
            assertThat(entry.getKafkaTopic()).isEqualTo("transaction.approved");
            assertThat(entry.getEventPayload().get("fraudScore")).isEqualTo(0.05);
            assertThat(entry.getEventPayload().get("modelVersion")).isEqualTo("v1.0.0");

            Boolean hasKey = redisTemplate.hasKey("processed:" + eventId);
            assertThat(hasKey).isTrue();
        });

        // 3. Publish DUPLICATE event to verify idempotency
        testKafkaTemplate.send(TransactionApprovedConsumer.TOPIC, transactionId.toString(), json).get(5, TimeUnit.SECONDS);

        Thread.sleep(1500);

        // 4. Verify MongoDB still has exactly 1 entry
        Query query = new Query(Criteria.where("transactionId").is(transactionId));
        List<LedgerEntry> entriesAfterDuplicate = mongoTemplate.find(query, LedgerEntry.class);
        assertThat(entriesAfterDuplicate).hasSize(1);
    }
}
