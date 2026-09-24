package com.fraudetection.auth_service;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.postgresql.PostgreSQLContainer;

/**
 * Boots the full application context against real containers, so it runs anywhere Docker is
 * available (locally and in CI) without the service's environment variables. Needs Docker.
 */
@SpringBootTest
class AuthServiceApplicationTests {

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

	@Test
	void contextLoads() {
	}

}
