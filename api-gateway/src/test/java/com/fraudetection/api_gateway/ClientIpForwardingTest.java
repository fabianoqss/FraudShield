package com.fraudetection.api_gateway;

import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class ClientIpForwardingTest {
    private static final HttpServer DOWNSTREAM = startDownstream();

    @LocalServerPort
    int port;

    @MockitoBean
    JwtDecoder jwtDecoder;

    @DynamicPropertySource
    static void routes(DynamicPropertyRegistry registry) {
        registry.add("AUTH_SERVICE_URL", () -> "http://127.0.0.1:" + DOWNSTREAM.getAddress().getPort());
    }

    @AfterAll
    static void stopDownstream() {
        DOWNSTREAM.stop(0);
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void forwardsSocketPeerAndDiscardsForgedHeaders(boolean forged) throws Exception {
        var request = HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + port + "/auth/login"));
        if (forged) {
            request.header("X-Forwarded-For", "8.8.8.8, 1.1.1.1")
                    .header("X-Forwarded-For", "9.9.9.9")
                    .header("Forwarded", "for=8.8.8.8;proto=https");
        }
        try (var client = HttpClient.newHttpClient()) {
            var response = client.send(request.POST(HttpRequest.BodyPublishers.noBody()).build(),
                    HttpResponse.BodyHandlers.ofString());
            assertThat(response.statusCode()).isEqualTo(200);
            assertThat(response.body()).isEqualTo("127.0.0.1|absent");
        }
    }

    private static HttpServer startDownstream() {
        try {
            var server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
            server.createContext("/", exchange -> {
                var headers = exchange.getRequestHeaders();
                String forwarded = headers.getFirst("Forwarded");
                byte[] body = (String.join(",", headers.getOrDefault("X-Forwarded-For", java.util.List.of()))
                        + "|" + (forwarded == null ? "absent" : forwarded)).getBytes(StandardCharsets.UTF_8);
                exchange.sendResponseHeaders(200, body.length);
                exchange.getResponseBody().write(body);
                exchange.close();
            });
            server.start();
            return server;
        } catch (IOException ex) {
            throw new UncheckedIOException(ex);
        }
    }
}
