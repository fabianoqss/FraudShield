package com.fraudetection.account_service.clients;

import com.fraudetection.account_service.dto.response.UserLookupResponse;
import com.fraudetection.account_service.services.exceptions.AuthServiceUnavailableException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.ExpectedCount.once;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

class AuthServiceClientTest {

    private static final UUID USER_ID = UUID.fromString("7d3f0c1a-2b4e-4c6d-8e9f-0a1b2c3d4e5f");
    private static final String LOOKUP_URL = "http://auth/auth/users/lookup?id=" + USER_ID;

    private MockRestServiceServer server;
    private AuthServiceClient client;

    @BeforeEach
    void setUp() {
        RestClient.Builder builder = RestClient.builder().baseUrl("http://auth");
        server = MockRestServiceServer.bindTo(builder).build();
        RestClient restClient = builder.build();

        Clock clock = Clock.fixed(Instant.parse("2026-09-22T12:00:00Z"), ZoneOffset.UTC);
        ServiceTokenProvider tokenProvider = new ServiceTokenProvider(restClient, "account-service", "s3cret", clock);
        client = new AuthServiceClient(restClient, tokenProvider);
    }

    @Test
    void lookupSendsServiceTokenAndReusesItAcrossCalls() {
        expectServiceToken("token-1");
        expectLookup("token-1", USER_ID);
        expectLookup("token-1", USER_ID);

        UserLookupResponse first = client.lookupById(USER_ID);
        client.lookupById(USER_ID);

        assertThat(first.userId()).isEqualTo(USER_ID);
        server.verify();
    }

    @Test
    void lookupFetchesNewTokenOnceWhenCachedTokenIsRejected() {
        expectServiceToken("stale");
        server.expect(once(), requestTo(LOOKUP_URL))
                .andExpect(header(HttpHeaders.AUTHORIZATION, "Bearer stale"))
                .andRespond(withStatus(HttpStatus.UNAUTHORIZED));
        expectServiceToken("fresh");
        expectLookup("fresh", USER_ID);

        assertThat(client.lookupById(USER_ID).userId()).isEqualTo(USER_ID);
        server.verify();
    }

    @Test
    void rejectedClientCredentialsMapToUnavailable() {
        server.expect(once(), requestTo("http://auth/auth/service-token")).andRespond(withStatus(HttpStatus.UNAUTHORIZED));

        assertThatThrownBy(() -> client.lookupById(USER_ID))
                .isInstanceOf(AuthServiceUnavailableException.class);
    }

    @Test
    void lookupByIdSendsTheIdAsQueryParameter() {
        UUID userId = UUID.randomUUID();
        expectServiceToken("token-1");
        server.expect(once(), requestTo("http://auth/auth/users/lookup?id=" + userId))
                .andExpect(header(HttpHeaders.AUTHORIZATION, "Bearer token-1"))
                .andRespond(withSuccess("""
                        {"userId":"%s","fullName":"Ana Souza","email":"ana@example.com","cpf":"52998224725"}
                        """.formatted(userId), MediaType.APPLICATION_JSON));

        UserLookupResponse user = client.lookupById(userId);

        assertThat(user.cpf()).isEqualTo("52998224725");
        server.verify();
    }

    @Test
    void lookupByIdOfUnknownUserIsAnInvariantViolation() {
        UUID userId = UUID.randomUUID();
        expectServiceToken("token-1");
        server.expect(once(), requestTo("http://auth/auth/users/lookup?id=" + userId))
                .andRespond(withStatus(HttpStatus.NOT_FOUND));

        assertThatThrownBy(() -> client.lookupById(userId)).isInstanceOf(IllegalStateException.class);
    }

    @Test
    void lookupByIdMapsServerErrorsToUnavailable() {
        UUID userId = UUID.randomUUID();
        expectServiceToken("token-1");
        server.expect(once(), requestTo("http://auth/auth/users/lookup?id=" + userId))
                .andRespond(withStatus(HttpStatus.INTERNAL_SERVER_ERROR));

        assertThatThrownBy(() -> client.lookupById(userId)).isInstanceOf(AuthServiceUnavailableException.class);
    }

    private void expectServiceToken(String token) {
        server.expect(once(), requestTo("http://auth/auth/service-token"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(content().json("""
                        {"clientId":"account-service","clientSecret":"s3cret"}
                        """))
                .andRespond(withSuccess("""
                        {"accessToken":"%s","tokenType":"Bearer","expiresIn":300}
                        """.formatted(token), MediaType.APPLICATION_JSON));
    }

    private void expectLookup(String token, UUID userId) {
        server.expect(once(), requestTo(LOOKUP_URL))
                .andExpect(header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andRespond(withSuccess("""
                        {"userId":"%s","fullName":"Ana Souza","email":"ana@example.com","cpf":"52998224725"}
                        """.formatted(userId), MediaType.APPLICATION_JSON));
    }
}
