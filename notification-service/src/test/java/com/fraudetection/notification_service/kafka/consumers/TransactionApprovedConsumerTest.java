package com.fraudetection.notification_service.kafka.consumers;

import com.fraudetection.notification_service.dto.NotificationRequest;
import com.fraudetection.notification_service.dto.event.KafkaEventEnvelope;
import com.fraudetection.notification_service.dto.event.TransactionApprovedPayload;
import com.fraudetection.notification_service.services.NotificationService;
import org.apache.kafka.clients.consumer.Consumer;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.producer.Producer;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.common.serialization.ByteArrayDeserializer;
import org.apache.kafka.common.serialization.ByteArraySerializer;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.apache.kafka.common.serialization.StringSerializer;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.kafka.core.DefaultKafkaConsumerFactory;
import org.springframework.kafka.core.DefaultKafkaProducerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.test.EmbeddedKafkaBroker;
import org.springframework.kafka.test.context.EmbeddedKafka;
import org.springframework.kafka.test.utils.KafkaTestUtils;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

@SpringBootTest
@EmbeddedKafka(partitions = 1, topics = {"transaction.approved", "transaction.approved-dlt"},
        bootstrapServersProperty = "spring.kafka.bootstrap-servers")
@Testcontainers
class TransactionApprovedConsumerTest {

    private static final String TOPIC = "transaction.approved";
    // Spring Kafka's DeadLetterPublishingRecoverer default suffix is "-dlt" (lowercase, hyphen) — not ".DLT".
    private static final String DLT_TOPIC = "transaction.approved-dlt";

    @Container
    static final GenericContainer<?> redis = new GenericContainer<>("redis:7-alpine").withExposedPorts(6379);

    @DynamicPropertySource
    static void redisProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.data.redis.host", redis::getHost);
        registry.add("spring.data.redis.port", () -> redis.getMappedPort(6379));
    }

    @Autowired
    private KafkaTemplate<String, Object> kafkaTemplate;

    @Autowired
    private EmbeddedKafkaBroker embeddedKafkaBroker;

    @MockitoBean
    private NotificationService notificationService;

    @Test
    void consumirEventoTransactionApproved_shouldNotificar_whenEventoAindaNaoFoiProcessado() {
        UUID eventId = UUID.randomUUID();

        kafkaTemplate.send(TOPIC, eventId.toString(), buildEnvelope(eventId));

        await().atMost(Duration.ofSeconds(10))
                .untilAsserted(() -> verify(notificationService, times(1)).notify(any(NotificationRequest.class)));
    }

    @Test
    void consumirEventoTransactionApproved_shouldIgnorarProcessamento_whenEventoJaFoiProcessado() {
        UUID eventId = UUID.randomUUID();
        KafkaEventEnvelope envelope = buildEnvelope(eventId);

        kafkaTemplate.send(TOPIC, eventId.toString(), envelope);
        await().atMost(Duration.ofSeconds(10))
                .untilAsserted(() -> verify(notificationService, times(1)).notify(any(NotificationRequest.class)));

        kafkaTemplate.send(TOPIC, eventId.toString(), envelope);
        await().pollDelay(Duration.ofSeconds(2)).atMost(Duration.ofSeconds(10))
                .untilAsserted(() -> verify(notificationService, times(1)).notify(any(NotificationRequest.class)));
    }

    @Test
    void consumirMensagemMalformada_shouldSerEncaminhadoParaDLT_whenDeserializacaoFalha() {
        Map<String, Object> producerProps = new HashMap<>(KafkaTestUtils.producerProps(embeddedKafkaBroker));
        producerProps.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class);
        producerProps.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, ByteArraySerializer.class);

        try (Producer<String, byte[]> rawProducer = new DefaultKafkaProducerFactory<String, byte[]>(producerProps).createProducer()) {
            rawProducer.send(new ProducerRecord<>(TOPIC, "poison-pill", "this is not json".getBytes(StandardCharsets.UTF_8)));
            rawProducer.flush();
        }

        Map<String, Object> consumerProps = new HashMap<>(
                KafkaTestUtils.consumerProps("dlt-verification-group", "true", embeddedKafkaBroker));
        consumerProps.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);
        consumerProps.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, ByteArrayDeserializer.class);
        consumerProps.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");

        try (Consumer<String, byte[]> dltConsumer = new DefaultKafkaConsumerFactory<String, byte[]>(consumerProps).createConsumer()) {
            embeddedKafkaBroker.consumeFromAnEmbeddedTopic(dltConsumer, DLT_TOPIC);
            ConsumerRecord<String, byte[]> record =
                    KafkaTestUtils.getSingleRecord(dltConsumer, DLT_TOPIC, Duration.ofSeconds(10));
            assertThat(new String(record.value(), StandardCharsets.UTF_8)).isEqualTo("this is not json");
        }
    }

    private KafkaEventEnvelope buildEnvelope(UUID eventId) {
        TransactionApprovedPayload payload = new TransactionApprovedPayload(
                UUID.randomUUID(),
                UUID.randomUUID(),
                UUID.randomUUID(),
                new BigDecimal("500.00"),
                new BigDecimal("0.10"),
                "v1.0.0",
                Instant.now()
        );
        return new KafkaEventEnvelope(eventId, "TRANSACTION_APPROVED", Instant.now(), payload);
    }
}
