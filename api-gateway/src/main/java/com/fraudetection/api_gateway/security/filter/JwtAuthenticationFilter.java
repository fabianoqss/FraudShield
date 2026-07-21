package com.fraudetection.api_gateway.security.filter;

import com.fraudetection.api_gateway.security.JwtService;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Slf4j
@Component
@RequiredArgsConstructor
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private static final String BEARER_PREFIX = "Bearer ";
    static final String USER_ID_HEADER = "X-User-Id";
    static final String USER_EMAIL_HEADER = "X-User-Email";

    private final JwtService jwtService;

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain
    ) throws ServletException, IOException {
        String header = request.getHeader("Authorization");

        if (header == null || !header.startsWith(BEARER_PREFIX)) {
            filterChain.doFilter(new UserContextRequestWrapper(request, Map.of()), response);
            return;
        }

        String token = header.substring(BEARER_PREFIX.length());
        Claims claims;

        try {
            claims = jwtService.parseClaims(token);
        } catch (JwtException | IllegalArgumentException e) {
            log.warn("Rejected invalid JWT: {}", e.getMessage());
            SecurityContextHolder.clearContext();
            response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
            response.setContentType("application/json");
            response.getWriter().write("{\"error\":\"Invalid or expired token\"}");
            return;
        }

        var authentication = new UsernamePasswordAuthenticationToken(
                claims.getSubject(), null, List.of()
        );
        SecurityContextHolder.getContext().setAuthentication(authentication);

        Map<String, String> identityHeaders = new LinkedHashMap<>();
        identityHeaders.put(USER_ID_HEADER, claims.getSubject());
        String email = claims.get("email", String.class);
        if (email != null) {
            identityHeaders.put(USER_EMAIL_HEADER, email);
        }

        filterChain.doFilter(new UserContextRequestWrapper(request, identityHeaders), response);
    }
}
