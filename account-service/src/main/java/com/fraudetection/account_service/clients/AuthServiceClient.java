package com.fraudetection.account_service.clients;

import com.fraudetection.account_service.dto.response.UserLookupResponse;
import com.fraudetection.account_service.services.exceptions.AuthServiceUnavailableException;
import com.fraudetection.account_service.services.exceptions.PixKeyNotFoundException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

@Slf4j
@Component
public class AuthServiceClient {

    private final RestClient restClient;
    private final ServiceTokenProvider serviceTokenProvider;

    @Autowired
    public AuthServiceClient(@Value("${AUTH_SERVICE_URL:http://localhost:8081}") String authServiceUrl,
                             ServiceTokenProvider serviceTokenProvider) {
        this(RestClient.create(authServiceUrl), serviceTokenProvider);
    }

    AuthServiceClient(RestClient restClient, ServiceTokenProvider serviceTokenProvider) {
        this.restClient = restClient;
        this.serviceTokenProvider = serviceTokenProvider;
    }

    public UserLookupResponse lookupByEmail(String email) {
        return lookup("email", email);
    }

    public UserLookupResponse lookupByCpf(String cpf) {
        return lookup("cpf", cpf);
    }

    private UserLookupResponse lookup(String paramName, String paramValue) {
        try {
            String token = serviceTokenProvider.getToken();
            try {
                return callLookup(paramName, paramValue, token);
            } catch (HttpClientErrorException.Unauthorized e) {
                log.warn("auth-service rejected the cached service token, fetching a new one");
                serviceTokenProvider.invalidate();
                return callLookup(paramName, paramValue, serviceTokenProvider.getToken());
            }
        } catch (HttpClientErrorException.NotFound e) {
            throw new PixKeyNotFoundException();
        } catch (RestClientException e) {
            log.error("Failed to resolve PIX key with auth-service", e);
            throw new AuthServiceUnavailableException();
        }
    }

    private UserLookupResponse callLookup(String paramName, String paramValue, String token) {
        return restClient.get()
                .uri(uriBuilder -> uriBuilder.path("/auth/users/lookup").queryParam(paramName, paramValue).build())
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                .retrieve()
                .body(UserLookupResponse.class);
    }
}
