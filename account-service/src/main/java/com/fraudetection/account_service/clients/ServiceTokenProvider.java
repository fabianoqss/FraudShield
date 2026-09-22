package com.fraudetection.account_service.clients;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.time.Clock;
import java.time.Instant;
import java.util.Map;

@Component
public class ServiceTokenProvider {

    private static final long REFRESH_MARGIN_SECONDS = 30;

    private final RestClient restClient;
    private final String clientId;
    private final String clientSecret;
    private final Clock clock;

    private String cachedToken;
    private Instant expiresAt = Instant.EPOCH;

    @Autowired
    public ServiceTokenProvider(@Value("${AUTH_SERVICE_URL:http://localhost:8081}") String authServiceUrl,
                                @Value("${service-client.id}") String clientId,
                                @Value("${service-client.secret}") String clientSecret) {
        this(RestClient.create(authServiceUrl), clientId, clientSecret, Clock.systemUTC());
    }

    ServiceTokenProvider(RestClient restClient, String clientId, String clientSecret, Clock clock) {
        this.restClient = restClient;
        this.clientId = clientId;
        this.clientSecret = clientSecret;
        this.clock = clock;
    }

    public synchronized String getToken() {
        if (cachedToken == null || !clock.instant().isBefore(expiresAt)) {
            ServiceTokenResponse response = restClient.post()
                    .uri("/auth/service-token")
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(Map.of("clientId", clientId, "clientSecret", clientSecret))
                    .retrieve()
                    .body(ServiceTokenResponse.class);

            cachedToken = response.accessToken();
            expiresAt = clock.instant().plusSeconds(Math.max(0, response.expiresIn() - REFRESH_MARGIN_SECONDS));
        }
        return cachedToken;
    }

    public synchronized void invalidate() {
        cachedToken = null;
    }

    record ServiceTokenResponse(String accessToken, String tokenType, long expiresIn) {
    }
}
