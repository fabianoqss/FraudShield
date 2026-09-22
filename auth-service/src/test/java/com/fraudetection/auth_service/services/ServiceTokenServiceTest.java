package com.fraudetection.auth_service.services;

import com.fraudetection.auth_service.config.ServiceClientProperties;
import com.fraudetection.auth_service.dto.request.ServiceTokenRequest;
import com.fraudetection.auth_service.dto.response.ServiceTokenResponse;
import com.fraudetection.auth_service.security.JwtService;
import com.fraudetection.auth_service.services.exceptions.InvalidClientCredentialsException;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ServiceTokenServiceTest {

    private final JwtService jwtService = mock(JwtService.class);

    @Test
    void issuesTokenWithConfiguredScopesForValidCredentials() {
        ServiceTokenService service = serviceWith("s3cret");
        when(jwtService.generateServiceToken("account-service", List.of("users:lookup"))).thenReturn("signed");
        when(jwtService.getServiceTokenExpirationSeconds()).thenReturn(300L);

        ServiceTokenResponse response = service.issue(new ServiceTokenRequest("account-service", "s3cret"));

        assertThat(response.accessToken()).isEqualTo("signed");
        assertThat(response.tokenType()).isEqualTo("Bearer");
        assertThat(response.expiresIn()).isEqualTo(300L);
    }

    @Test
    void rejectsWrongSecret() {
        ServiceTokenService service = serviceWith("s3cret");

        assertThatThrownBy(() -> service.issue(new ServiceTokenRequest("account-service", "wrong")))
                .isInstanceOf(InvalidClientCredentialsException.class);
        verify(jwtService, never()).generateServiceToken(anyString(), anyList());
    }

    @Test
    void rejectsUnknownClient() {
        ServiceTokenService service = serviceWith("s3cret");

        assertThatThrownBy(() -> service.issue(new ServiceTokenRequest("ledger-service", "s3cret")))
                .isInstanceOf(InvalidClientCredentialsException.class);
    }

    @Test
    void rejectsClientWithoutConfiguredSecret() {
        ServiceTokenService service = serviceWith("");

        assertThatThrownBy(() -> service.issue(new ServiceTokenRequest("account-service", "")))
                .isInstanceOf(InvalidClientCredentialsException.class);
    }

    private ServiceTokenService serviceWith(String secret) {
        ServiceClientProperties properties = new ServiceClientProperties(Map.of(
                "account-service", new ServiceClientProperties.Client(secret, List.of("users:lookup"))
        ));
        return new ServiceTokenService(properties, jwtService);
    }
}
