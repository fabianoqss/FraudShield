package com.fraudetection.api_gateway;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.cloud.gateway.server.mvc.filter.HttpHeadersFilter.RequestHttpHeadersFilter;
import org.springframework.core.env.Environment;
import org.springframework.http.HttpHeaders;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.web.servlet.function.ServerRequest;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
class ClientIpHeadersTest {
    @Autowired
    List<RequestHttpHeadersFilter> filters;

    @Autowired
    Environment environment;

    @MockitoBean
    JwtDecoder jwtDecoder;

    @ParameterizedTest
    @ValueSource(strings = {"203.0.113.42", "2001:db8::42", "127.0.0.1"})
    void configuredFiltersReplaceClientHeadersWithSocketPeer(String peer) {
        var servletRequest = new MockHttpServletRequest("POST", "/transactions");
        servletRequest.setRemoteAddr(peer);
        servletRequest.addHeader("X-Forwarded-For", "8.8.8.8, 1.1.1.1");
        servletRequest.addHeader("X-Forwarded-For", "9.9.9.9");
        servletRequest.addHeader("Forwarded", "for=8.8.8.8");
        var request = ServerRequest.create(servletRequest, List.of());
        HttpHeaders headers = request.headers().asHttpHeaders();
        for (var filter : filters) {
            headers = filter.apply(headers, request);
        }
        assertThat(headers.get("X-Forwarded-For")).containsExactly(peer);
        assertThat(headers.containsHeader("Forwarded")).isFalse();
        assertThat(environment.getProperty("server.forward-headers-strategy")).isEqualTo("none");
    }
}
