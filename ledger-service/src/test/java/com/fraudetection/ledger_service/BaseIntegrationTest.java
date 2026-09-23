package com.fraudetection.ledger_service;

import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.common.serialization.StringSerializer;
import org.junit.jupiter.api.AfterEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.kafka.core.DefaultKafkaProducerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.kafka.KafkaContainer;
import tools.jackson.databind.ObjectMapper;

import java.util.Map;
import java.util.Set;

/**
 * Runs the ledger against real MongoDB, Kafka and Redis containers. The containers are
 * started once per JVM and shared by every subclass. Needs Docker.
 */
@SpringBootTest
public abstract class BaseIntegrationTest {

    private static final String MONGO_USER = "ledger";
    private static final String MONGO_PASSWORD = "ledger";

    protected static final GenericContainer<?> MONGO = new GenericContainer<>("mongo:7")
            .withEnv("MONGO_INITDB_ROOT_USERNAME", MONGO_USER)
            .withEnv("MONGO_INITDB_ROOT_PASSWORD", MONGO_PASSWORD)
            .withExposedPorts(27017);
    protected static final KafkaContainer KAFKA = new KafkaContainer("apache/kafka:3.9.1");
    protected static final GenericContainer<?> REDIS = new GenericContainer<>("redis:7-alpine")
            .withExposedPorts(6379);

    static {
        MONGO.start();
        KAFKA.start();
        REDIS.start();
    }

    @DynamicPropertySource
    static void containerProperties(DynamicPropertyRegistry registry) {
        registry.add("MONGO_LEDGER_HOST", MONGO::getHost);
        registry.add("MONGO_LEDGER_PORT", () -> MONGO.getMappedPort(27017));
        registry.add("MONGO_LEDGER_DB", () -> "ledger_test");
        registry.add("MONGO_LEDGER_USER", () -> MONGO_USER);
        registry.add("MONGO_LEDGER_PASSWORD", () -> MONGO_PASSWORD);
        registry.add("KAFKA_BOOTSTRAP_SERVERS", KAFKA::getBootstrapServers);
        registry.add("REDIS_HOST", REDIS::getHost);
        registry.add("REDIS_PORT", () -> REDIS.getMappedPort(6379));
    }

    // Publishes raw JSON strings, as the other services do, instead of going through the
    // ledger's own serializers.
    protected static final KafkaTemplate<String, String> PRODUCER = new KafkaTemplate<>(
            new DefaultKafkaProducerFactory<>(Map.of(
                    ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, KAFKA.getBootstrapServers(),
                    ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class,
                    ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, StringSerializer.class)));

    @Autowired
    protected MongoTemplate mongoTemplate;

    @Autowired
    protected StringRedisTemplate redisTemplate;

    @Autowired
    protected ObjectMapper objectMapper;

    @AfterEach
    void cleanUp() {
        mongoTemplate.getDb().drop();
        Set<String> keys = redisTemplate.keys("processed:*");
        if (keys != null && !keys.isEmpty()) {
            redisTemplate.delete(keys);
        }
    }
}
