package com.fraudetection.transaction_service;

import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.kafka.KafkaContainer;
import org.testcontainers.postgresql.PostgreSQLContainer;

/**
 * Boots the full application context against real Postgres and Kafka containers, started once per JVM and
 * shared by every subclass, so it runs anywhere Docker is available (locally and in CI) without the service's
 * environment variables. Needs Docker.
 */
@SpringBootTest
public abstract class AbstractIntegrationTest {

    private static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer("postgres:16-alpine");
    private static final KafkaContainer KAFKA = new KafkaContainer("apache/kafka:3.9.1");

    static {
        POSTGRES.start();
        KAFKA.start();
    }

    @DynamicPropertySource
    static void containerProperties(DynamicPropertyRegistry registry) {
        registry.add("POSTGRES_TRANSACTION_URL", POSTGRES::getJdbcUrl);
        registry.add("POSTGRES_TRANSACTION_USER", POSTGRES::getUsername);
        registry.add("POSTGRES_TRANSACTION_PASSWORD", POSTGRES::getPassword);
        registry.add("KAFKA_BOOTSTRAP_SERVERS", KAFKA::getBootstrapServers);
    }
}
