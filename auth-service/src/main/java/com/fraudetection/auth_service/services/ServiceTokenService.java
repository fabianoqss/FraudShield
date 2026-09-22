package com.fraudetection.auth_service.services;

import com.fraudetection.auth_service.config.ServiceClientProperties;
import com.fraudetection.auth_service.dto.request.ServiceTokenRequest;
import com.fraudetection.auth_service.dto.response.ServiceTokenResponse;
import com.fraudetection.auth_service.security.JwtService;
import com.fraudetection.auth_service.services.exceptions.InvalidClientCredentialsException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;

@Service
@RequiredArgsConstructor
public class ServiceTokenService {

    private final ServiceClientProperties serviceClientProperties;
    private final JwtService jwtService;

    public ServiceTokenResponse issue(ServiceTokenRequest request) {
        ServiceClientProperties.Client client = serviceClientProperties.clients().get(request.clientId());

        if (client == null || client.secret() == null || client.secret().isBlank()
                || !secretsMatch(client.secret(), request.clientSecret())) {
            throw new InvalidClientCredentialsException();
        }

        String accessToken = jwtService.generateServiceToken(request.clientId(), client.scopes());
        return new ServiceTokenResponse(accessToken, "Bearer", jwtService.getServiceTokenExpirationSeconds());
    }

    private boolean secretsMatch(String expected, String provided) {
        return MessageDigest.isEqual(
                expected.getBytes(StandardCharsets.UTF_8),
                provided.getBytes(StandardCharsets.UTF_8)
        );
    }
}
