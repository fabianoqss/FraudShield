package com.fraudetection.api_gateway;

import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;

import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class ApiDocsRoutingTest {

    // Each fake service answers with its own name and the path it was asked for.
    private static final HttpServer AUTH = startDownstream("auth");
    private static final HttpServer ACCOUNT = startDownstream("account");
    private static final HttpServer TRANSACTION = startDownstream("transaction");
    private static final HttpServer LEDGER = startDownstream("ledger");

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private JwtDecoder jwtDecoder;

    @DynamicPropertySource
    static void routes(DynamicPropertyRegistry registry) {
        registry.add("AUTH_SERVICE_URL", () -> urlOf(AUTH));
        registry.add("ACCOUNT_SERVICE_URL", () -> urlOf(ACCOUNT));
        registry.add("TRANSACTION_SERVICE_URL", () -> urlOf(TRANSACTION));
        registry.add("LEDGER_SERVICE_URL", () -> urlOf(LEDGER));
    }

    @AfterAll
    static void stopDownstreams() {
        AUTH.stop(0);
        ACCOUNT.stop(0);
        TRANSACTION.stop(0);
        LEDGER.stop(0);
    }

    @ParameterizedTest
    @ValueSource(strings = {"auth", "account", "transaction", "ledger"})
    void eachServiceSpecIsFetchedFromThatServiceWithoutToken(String service) throws Exception {
        mockMvc.perform(get("/api-docs/{service}", service))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.service").value(service))
                .andExpect(jsonPath("$.path").value("/v3/api-docs"));
    }

    @Test
    void swaggerUiIsPublic() throws Exception {
        mockMvc.perform(get("/swagger-ui/index.html"))
                .andExpect(status().isOk());
    }

    @Test
    void swaggerUiListsOnlyTheServiceSpecs() throws Exception {
        mockMvc.perform(get("/v3/api-docs/swagger-config"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.urls[*].url").value(containsInAnyOrder(
                        "/api-docs/auth", "/api-docs/account", "/api-docs/transaction", "/api-docs/ledger")));
    }

    private static String urlOf(HttpServer server) {
        return "http://localhost:" + server.getAddress().getPort();
    }

    private static HttpServer startDownstream(String name) {
        try {
            HttpServer server = HttpServer.create(new InetSocketAddress("localhost", 0), 0);
            server.createContext("/", exchange -> {
                byte[] body = ("{\"service\":\"" + name + "\",\"path\":\"" + exchange.getRequestURI().getPath() + "\"}")
                        .getBytes(StandardCharsets.UTF_8);
                exchange.getResponseHeaders().add("Content-Type", "application/json");
                exchange.sendResponseHeaders(200, body.length);
                exchange.getResponseBody().write(body);
                exchange.close();
            });
            server.start();
            return server;
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
