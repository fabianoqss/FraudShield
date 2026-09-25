package com.fraudetection.transaction_service.services;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.mock.web.MockHttpServletRequest;

import static org.assertj.core.api.Assertions.assertThat;

class ClientIpResolverTest {
    private final ClientIpResolver resolver = new ClientIpResolver();

    @Test
    void usesRightmostValueAcrossMultipleHeaderLines() {
        var request = new MockHttpServletRequest();
        request.addHeader("X-Forwarded-For", "8.8.8.8, 1.1.1.1");
        request.addHeader("X-Forwarded-For", "9.9.9.9,  2001:db8::1 ");
        assertThat(resolver.resolve(request)).isEqualTo("2001:db8::1");
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {" ", "8.8.8.8, "})
    void fallsBackToSocketPeerWhenLastValueIsAbsent(String header) {
        var request = new MockHttpServletRequest();
        request.setRemoteAddr("192.0.2.1");
        if (header != null) request.addHeader("X-Forwarded-For", header);
        assertThat(resolver.resolve(request)).isEqualTo("192.0.2.1");
    }
}
