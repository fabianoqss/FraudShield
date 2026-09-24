package com.fraudetection.account_service;

import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.kafka.KafkaContainer;
import org.testcontainers.postgresql.PostgreSQLContainer;

/**
 * Runs the service against real Postgres, Kafka and Redis containers, started once per JVM and shared by
 * every subclass. Needs Docker.
 */
@SpringBootTest
public abstract class AbstractIntegrationTest {

    private static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer("postgres:16-alpine");
    private static final KafkaContainer KAFKA = new KafkaContainer("apache/kafka:3.9.1");
    private static final GenericContainer<?> REDIS = new GenericContainer<>("redis:7.4-alpine")
            .withExposedPorts(6379);

    static {
        POSTGRES.start();
        KAFKA.start();
        REDIS.start();
    }

    @DynamicPropertySource
    static void containerProperties(DynamicPropertyRegistry registry) {
        registry.add("POSTGRES_ACCOUNT_URL", POSTGRES::getJdbcUrl);
        registry.add("POSTGRES_ACCOUNT_USER", POSTGRES::getUsername);
        registry.add("POSTGRES_ACCOUNT_PASSWORD", POSTGRES::getPassword);
        registry.add("KAFKA_BOOTSTRAP_SERVERS", KAFKA::getBootstrapServers);
        registry.add("REDIS_HOST", REDIS::getHost);
        registry.add("REDIS_PORT", () -> REDIS.getMappedPort(6379));
    }
}
