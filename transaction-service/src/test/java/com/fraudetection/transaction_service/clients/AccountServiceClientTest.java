package com.fraudetection.transaction_service.clients;

import com.fraudetection.transaction_service.services.exceptions.AccountServiceUnavailableException;
import com.fraudetection.transaction_service.services.exceptions.InvalidPixLookupException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.ExpectedCount.once;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

class AccountServiceClientTest {

    private MockRestServiceServer server;
    private AccountServiceClient client;

    @BeforeEach
    void setUp() {
        RestClient.Builder builder = RestClient.builder().baseUrl("http://account");
        server = MockRestServiceServer.bindTo(builder).build();
        client = new AccountServiceClient(builder.build());
    }

    @Test
    void resolvesALookupToTheDestinationAccount() {
        UUID lookupId = UUID.randomUUID();
        UUID accountId = UUID.randomUUID();
        server.expect(once(), requestTo("http://account/internal/pix-keys/lookups/" + lookupId))
                .andRespond(withSuccess("{\"destinationAccountId\":\"" + accountId + "\"}", MediaType.APPLICATION_JSON));

        assertThat(client.resolvePixLookup(lookupId)).isEqualTo(accountId);
    }

    @Test
    void unknownOrExpiredLookupIsInvalid() {
        UUID lookupId = UUID.randomUUID();
        server.expect(once(), requestTo("http://account/internal/pix-keys/lookups/" + lookupId))
                .andRespond(withStatus(HttpStatus.NOT_FOUND));

        assertThatThrownBy(() -> client.resolvePixLookup(lookupId)).isInstanceOf(InvalidPixLookupException.class);
    }

    @Test
    void accountServiceFailureIsUnavailable() {
        UUID lookupId = UUID.randomUUID();
        server.expect(once(), requestTo("http://account/internal/pix-keys/lookups/" + lookupId))
                .andRespond(withStatus(HttpStatus.SERVICE_UNAVAILABLE));

        assertThatThrownBy(() -> client.resolvePixLookup(lookupId))
                .isInstanceOf(AccountServiceUnavailableException.class);
    }
}
