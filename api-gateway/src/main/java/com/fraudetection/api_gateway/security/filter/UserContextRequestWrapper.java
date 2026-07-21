package com.fraudetection.api_gateway.security.filter;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletRequestWrapper;

import java.util.Collections;
import java.util.Enumeration;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

class UserContextRequestWrapper extends HttpServletRequestWrapper {

    private static final Set<String> CONTROLLED_HEADERS = Set.of(
            JwtAuthenticationFilter.USER_ID_HEADER,
            JwtAuthenticationFilter.USER_EMAIL_HEADER
    );

    private final Map<String, String> identityHeaders;

    UserContextRequestWrapper(HttpServletRequest request, Map<String, String> identityHeaders) {
        super(request);
        this.identityHeaders = identityHeaders;
    }

    @Override
    public String getHeader(String name) {
        String override = findOverride(name);
        if (override != null) {
            return override;
        }
        return isControlled(name) ? null : super.getHeader(name);
    }

    @Override
    public Enumeration<String> getHeaders(String name) {
        String override = findOverride(name);
        if (override != null) {
            return Collections.enumeration(Collections.singletonList(override));
        }
        return isControlled(name) ? Collections.emptyEnumeration() : super.getHeaders(name);
    }

    @Override
    public Enumeration<String> getHeaderNames() {
        Set<String> names = new LinkedHashSet<>();
        Collections.list(super.getHeaderNames()).forEach(name -> {
            if (!isControlled(name)) {
                names.add(name);
            }
        });
        names.addAll(identityHeaders.keySet());
        return Collections.enumeration(names);
    }

    private boolean isControlled(String name) {
        return CONTROLLED_HEADERS.stream().anyMatch(h -> h.equalsIgnoreCase(name));
    }

    private String findOverride(String name) {
        return identityHeaders.entrySet().stream()
                .filter(entry -> entry.getKey().equalsIgnoreCase(name))
                .map(Map.Entry::getValue)
                .findFirst()
                .orElse(null);
    }
}
