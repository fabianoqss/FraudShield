package com.fraudetection.transaction_service.clients;

import com.fraudetection.transaction_service.services.exceptions.AccountAccessDeniedException;
import com.fraudetection.transaction_service.services.exceptions.AccountNotFoundException;
import com.fraudetection.transaction_service.services.exceptions.AccountServiceUnavailableException;
import com.fraudetection.transaction_service.services.exceptions.InvalidPixLookupException;
import com.fraudetection.transaction_service.services.exceptions.SourceAccountAccessDeniedException;
import com.fraudetection.transaction_service.services.exceptions.SourceAccountNotFoundException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.math.BigDecimal;
import java.util.UUID;

@Slf4j
@Component
public class AccountServiceClient {

    private final RestClient restClient;

    @Autowired
    public AccountServiceClient(@Value("${ACCOUNT_SERVICE_URL:http://localhost:8082}") String accountServiceUrl,
                                BearerTokenInterceptor bearerTokenInterceptor) {
        this(RestClient.builder()
                .baseUrl(accountServiceUrl)
                .requestInterceptor(bearerTokenInterceptor)
                .build());
    }

    AccountServiceClient(RestClient restClient) {
        this.restClient = restClient;
    }

    public BigDecimal getOwnedAvailableBalance(UUID accountId) {
        try {
            BalanceResponse balance = restClient.get()
                    .uri("/accounts/{id}/balance", accountId)
                    .retrieve()
                    .body(BalanceResponse.class);
            return balance.availableBalance();
        } catch (HttpClientErrorException.NotFound e) {
            throw new SourceAccountNotFoundException(accountId);
        } catch (HttpClientErrorException.Forbidden e) {
            throw new SourceAccountAccessDeniedException(accountId);
        } catch (RestClientException e) {
            log.error("Failed to verify ownership of source account {} with account-service", accountId, e);
            throw new AccountServiceUnavailableException();
        }
    }

    public UUID resolvePixLookup(UUID lookupId) {
        try {
            PixLookupResolution resolution = restClient.get()
                    .uri("/internal/pix-keys/lookups/{id}", lookupId)
                    .retrieve()
                    .body(PixLookupResolution.class);
            return resolution.destinationAccountId();
        } catch (HttpClientErrorException.NotFound e) {
            throw new InvalidPixLookupException();
        } catch (RestClientException e) {
            log.error("Failed to resolve PIX lookup {} with account-service", lookupId, e);
            throw new AccountServiceUnavailableException();
        }
    }

    public boolean ownsAccount(UUID accountId) {
        try {
            restClient.get()
                    .uri("/accounts/{id}/balance", accountId)
                    .retrieve()
                    .toBodilessEntity();
            return true;
        } catch (HttpClientErrorException.NotFound | HttpClientErrorException.Forbidden e) {
            return false;
        } catch (RestClientException e) {
            log.error("Failed to check ownership of account {} with account-service", accountId, e);
            throw new AccountServiceUnavailableException();
        }
    }

    public void requireOwnedAccount(UUID accountId) {
        try {
            restClient.get()
                    .uri("/accounts/{id}/balance", accountId)
                    .retrieve()
                    .toBodilessEntity();
        } catch (HttpClientErrorException.NotFound e) {
            throw new AccountNotFoundException(accountId);
        } catch (HttpClientErrorException.Forbidden e) {
            throw new AccountAccessDeniedException(accountId);
        } catch (RestClientException e) {
            log.error("Failed to check ownership of account {} with account-service", accountId, e);
            throw new AccountServiceUnavailableException();
        }
    }

    private record BalanceResponse(BigDecimal availableBalance) {
    }

    private record PixLookupResolution(UUID destinationAccountId) {
    }
}
