package com.fraudetection.api_gateway.security;

import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.security.oauth2.jwt.BadJwtException;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.UUID;

import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class GatewaySecurityTest {

    private static final HttpServer DOWNSTREAM = startDownstream();

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private JwtDecoder jwtDecoder;

    @DynamicPropertySource
    static void routes(DynamicPropertyRegistry registry) {
        String downstreamUrl = "http://localhost:" + DOWNSTREAM.getAddress().getPort();
        registry.add("AUTH_SERVICE_URL", () -> downstreamUrl);
        registry.add("ACCOUNT_SERVICE_URL", () -> downstreamUrl);
        registry.add("TRANSACTION_SERVICE_URL", () -> downstreamUrl);
        registry.add("LEDGER_SERVICE_URL", () -> downstreamUrl);
    }

    @AfterAll
    static void stopDownstream() {
        DOWNSTREAM.stop(0);
    }

    @Test
    void protectedRouteWithoutTokenIsUnauthorized() throws Exception {
        mockMvc.perform(get("/transactions/{id}", UUID.randomUUID()))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void userHeaderAloneDoesNotAuthenticate() throws Exception {
        mockMvc.perform(get("/transactions/{id}", UUID.randomUUID())
                        .header("X-User-Id", UUID.randomUUID().toString()))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void malformedBearerTokenIsUnauthorized() throws Exception {
        when(jwtDecoder.decode("not-a-jwt")).thenThrow(new BadJwtException("Malformed token"));

        mockMvc.perform(get("/transactions/{id}", UUID.randomUUID())
                        .header("Authorization", "Bearer not-a-jwt"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void authenticatedRequestIsForwardedWithItsBearerToken() throws Exception {
        when(jwtDecoder.decode("user-token")).thenReturn(Jwt.withTokenValue("user-token")
                .header("alg", "RS256")
                .subject(UUID.randomUUID().toString())
                .issuedAt(Instant.now())
                .expiresAt(Instant.now().plusSeconds(60))
                .build());

        mockMvc.perform(get("/transactions/{id}", UUID.randomUUID())
                        .header("Authorization", "Bearer user-token"))
                .andExpect(status().isOk())
                .andExpect(content().string("Bearer user-token"));
    }

    @Test
    void publicAuthRoutesAreForwardedWithoutToken() throws Exception {
        mockMvc.perform(post("/auth/login"))
                .andExpect(status().isOk());
    }

    @Test
    void internalAuthEndpointsAreNotExposed() throws Exception {
        mockMvc.perform(post("/auth/service-token").with(jwt()))
                .andExpect(status().isForbidden());
        mockMvc.perform(get("/auth/users/lookup").param("email", "ana@example.com").with(jwt()))
                .andExpect(status().isForbidden());
    }

    private static HttpServer startDownstream() {
        try {
            HttpServer server = HttpServer.create(new InetSocketAddress("localhost", 0), 0);
            server.createContext("/", exchange -> {
                String authorization = exchange.getRequestHeaders().getFirst("Authorization");
                byte[] body = (authorization == null ? "" : authorization).getBytes(StandardCharsets.UTF_8);
                exchange.sendResponseHeaders(200, body.length == 0 ? -1 : body.length);
                if (body.length > 0) {
                    exchange.getResponseBody().write(body);
                }
                exchange.close();
            });
            server.start();
            return server;
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
