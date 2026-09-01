package com.fraudetection.ledger_service.clients;

import com.fraudetection.ledger_service.services.exceptions.AccountAccessDeniedException;
import com.fraudetection.ledger_service.services.exceptions.AccountNotFoundException;
import com.fraudetection.ledger_service.services.exceptions.AccountServiceUnavailableException;
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

    public void verifyOwnership(UUID accountId, UUID requestingUserId) {
        try {
            restClient.get()
                    .uri("/accounts/{id}/balance", accountId)
                    .header("X-User-Id", requestingUserId.toString())
                    .retrieve()
                    .toBodilessEntity();
        } catch (HttpClientErrorException.NotFound e) {
            throw new AccountNotFoundException(accountId);
        } catch (HttpClientErrorException.Forbidden e) {
            throw new AccountAccessDeniedException(accountId);
        } catch (RestClientException e) {
            log.error("Failed to verify ownership of account {} with account-service", accountId, e);
            throw new AccountServiceUnavailableException();
        }
    }
}
