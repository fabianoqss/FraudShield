package com.fraudetection.auth_service;

import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.postgresql.PostgreSQLContainer;

/**
 * Boots the full application context against a real Postgres container, started once per JVM and shared by
 * every subclass, so it runs anywhere Docker is available (locally and in CI) without the service's
 * environment variables. Needs Docker.
 */
@SpringBootTest
public abstract class AbstractIntegrationTest {

    private static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer("postgres:16-alpine");

    static {
        POSTGRES.start();
    }

    @DynamicPropertySource
    static void containerProperties(DynamicPropertyRegistry registry) {
        registry.add("POSTGRES_AUTH_URL", POSTGRES::getJdbcUrl);
        registry.add("POSTGRES_AUTH_USER", POSTGRES::getUsername);
        registry.add("POSTGRES_AUTH_PASSWORD", POSTGRES::getPassword);
    }
}
