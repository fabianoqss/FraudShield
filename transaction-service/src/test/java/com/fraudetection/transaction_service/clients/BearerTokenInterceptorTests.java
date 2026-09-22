package com.fraudetection.transaction_service.clients;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.client.ClientHttpRequestExecution;
import org.springframework.http.client.ClientHttpResponse;
import org.springframework.mock.http.client.MockClientHttpRequest;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;

import java.net.URI;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class BearerTokenInterceptorTests {

    private final BearerTokenInterceptor interceptor = new BearerTokenInterceptor();

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void propagatesCurrentJwtAndPreservesRequestBody() throws Exception {
        Jwt jwt = Jwt.withTokenValue("user-token").header("alg", "RS256").subject("user").build();
        SecurityContextHolder.getContext().setAuthentication(new JwtAuthenticationToken(jwt, java.util.List.of()));
        var request = new MockClientHttpRequest(HttpMethod.GET, URI.create("http://account/accounts/1/balance"));
        byte[] body = {1, 2};
        var execution = mock(ClientHttpRequestExecution.class);
        var response = mock(ClientHttpResponse.class);
        when(execution.execute(request, body)).thenReturn(response);

        assertSame(response, interceptor.intercept(request, body, execution));
        assertEquals("Bearer user-token", request.getHeaders().getFirst(HttpHeaders.AUTHORIZATION));
        assertNull(request.getHeaders().getFirst("X-User-Id"));
        verify(execution).execute(request, body);
    }

    @Test
    void doesNotInventTokenWithoutAuthentication() throws Exception {
        var request = new MockClientHttpRequest();
        var execution = mock(ClientHttpRequestExecution.class);

        interceptor.intercept(request, new byte[0], execution);

        assertNull(request.getHeaders().getFirst(HttpHeaders.AUTHORIZATION));
        verify(execution).execute(eq(request), any(byte[].class));
    }
}
