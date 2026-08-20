package com.fraudetection.account_service.clients;

import com.fraudetection.account_service.dto.response.UserLookupResponse;
import com.fraudetection.account_service.services.exceptions.AuthServiceUnavailableException;
import com.fraudetection.account_service.services.exceptions.PixKeyNotFoundException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

@Slf4j
@Component
public class AuthServiceClient {

    private final RestClient restClient;

    public AuthServiceClient(@Value("${AUTH_SERVICE_URL:http://localhost:8081}") String authServiceUrl) {
        this.restClient = RestClient.create(authServiceUrl);
    }

    public UserLookupResponse lookupByEmail(String email) {
        return lookup("email", email);
    }

    public UserLookupResponse lookupByCpf(String cpf) {
        return lookup("cpf", cpf);
    }

    private UserLookupResponse lookup(String paramName, String paramValue) {
        try {
            return restClient.get()
                    .uri(uriBuilder -> uriBuilder.path("/auth/users/lookup").queryParam(paramName, paramValue).build())
                    .retrieve()
                    .body(UserLookupResponse.class);
        } catch (HttpClientErrorException.NotFound e) {
            throw new PixKeyNotFoundException();
        } catch (RestClientException e) {
            log.error("Failed to resolve PIX key with auth-service", e);
            throw new AuthServiceUnavailableException();
        }
    }
}
