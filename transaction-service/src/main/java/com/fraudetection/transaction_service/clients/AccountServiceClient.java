package com.fraudetection.transaction_service.clients;

import com.fraudetection.transaction_service.services.exceptions.AccountServiceUnavailableException;
import com.fraudetection.transaction_service.services.exceptions.SourceAccountAccessDeniedException;
import com.fraudetection.transaction_service.services.exceptions.SourceAccountNotFoundException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.util.UUID;

@Slf4j
@Component
public class AccountServiceClient {

    private final RestClient restClient;

    public AccountServiceClient(@Value("${ACCOUNT_SERVICE_URL:http://localhost:8082}") String accountServiceUrl) {
        this.restClient = RestClient.create(accountServiceUrl);
    }

    public void verifySourceAccountOwnership(UUID accountId, UUID requestingUserId) {
        try {
            restClient.get()
                    .uri("/accounts/{id}/balance", accountId)
                    .header("X-User-Id", requestingUserId.toString())
                    .retrieve()
                    .toBodilessEntity();
        } catch (HttpClientErrorException.NotFound e) {
            throw new SourceAccountNotFoundException(accountId);
        } catch (HttpClientErrorException.Forbidden e) {
            throw new SourceAccountAccessDeniedException(accountId);
        } catch (RestClientException e) {
            log.error("Failed to verify ownership of source account {} with account-service", accountId, e);
            throw new AccountServiceUnavailableException();
        }
    }
}
