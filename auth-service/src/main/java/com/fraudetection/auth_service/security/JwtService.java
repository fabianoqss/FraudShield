package com.fraudetection.auth_service.security;

import com.fraudetection.auth_service.entities.User;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.oauth2.jose.jws.SignatureAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.List;

@Service
public class JwtService {

    private final JwtEncoder jwtEncoder;
    private final String issuer;
    private final long expirationMs;
    private final long serviceTokenExpirationMs;

    public JwtService(
            JwtEncoder jwtEncoder,
            @Value("${jwt.issuer}") String issuer,
            @Value("${jwt.expiration-ms}") long expirationMs,
            @Value("${jwt.service-token-expiration-ms}") long serviceTokenExpirationMs
    ) {
        this.jwtEncoder = jwtEncoder;
        this.issuer = issuer;
        this.expirationMs = expirationMs;
        this.serviceTokenExpirationMs = serviceTokenExpirationMs;
    }

    public String generateToken(User user) {
        Instant issuedAt = Instant.now();

        JwtClaimsSet claims = JwtClaimsSet.builder()
                .issuer(issuer)
                .subject(user.getId().toString())
                .claim("email", user.getEmail())
                .claim("fullName", user.getFullName())
                .issuedAt(issuedAt)
                .expiresAt(issuedAt.plusMillis(expirationMs))
                .build();

        return encode(claims);
    }

    public String generateServiceToken(String clientId, List<String> scopes) {
        Instant issuedAt = Instant.now();

        JwtClaimsSet claims = JwtClaimsSet.builder()
                .issuer(issuer)
                .subject(clientId)
                .claim("scope", String.join(" ", scopes))
                .issuedAt(issuedAt)
                .expiresAt(issuedAt.plusMillis(serviceTokenExpirationMs))
                .build();

        return encode(claims);
    }

    public long getExpirationSeconds() {
        return expirationMs / 1000;
    }

    public long getServiceTokenExpirationSeconds() {
        return serviceTokenExpirationMs / 1000;
    }

    private String encode(JwtClaimsSet claims) {
        JwsHeader header = JwsHeader.with(SignatureAlgorithm.RS256).build();
        return jwtEncoder.encode(JwtEncoderParameters.from(header, claims)).getTokenValue();
    }
}
