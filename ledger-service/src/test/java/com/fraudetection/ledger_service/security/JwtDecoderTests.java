package com.fraudetection.ledger_service.security;

import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.crypto.RSASSASigner;
import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jose.jwk.gen.RSAKeyGenerator;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtException;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import tools.jackson.databind.ObjectMapper;

import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Date;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class JwtDecoderTests {

    private static final String ISSUER = "fraudshield-auth-service";
    private static final String SUBJECT = UUID.randomUUID().toString();
    private HttpServer server;
    private RSAKey key;
    private JwtDecoder decoder;

    @BeforeEach
    void setUp() throws Exception {
        key = new RSAKeyGenerator(2048).keyID("test-key").generate();
        byte[] jwks = new JWKSet(key.toPublicJWK()).toString().getBytes(StandardCharsets.UTF_8);
        server = HttpServer.create(new InetSocketAddress("localhost", 0), 0);
        server.createContext("/jwks", exchange -> {
            exchange.getResponseHeaders().set("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, jwks.length);
            try (var output = exchange.getResponseBody()) {
                output.write(jwks);
            }
        });
        server.start();
        decoder = new SecurityConfig(new ObjectMapper()).jwtDecoder(
                "http://localhost:" + server.getAddress().getPort() + "/jwks", ISSUER);
    }

    @AfterEach
    void tearDown() {
        if (server != null) {
            server.stop(0);
        }
    }

    @Test
    void acceptsSignedRs256TokenAndUsesSubjectAsName() throws Exception {
        var jwt = decoder.decode(token(key, JWSAlgorithm.RS256, ISSUER, Instant.now().plusSeconds(300)));
        assertEquals(SUBJECT, new JwtAuthenticationToken(jwt).getName());
    }

    @Test
    void rejectsWrongIssuer() throws Exception {
        String token = token(key, JWSAlgorithm.RS256, "other-issuer", Instant.now().plusSeconds(300));
        assertThrows(JwtException.class, () -> decoder.decode(token));
    }

    @Test
    void rejectsExpiredToken() throws Exception {
        String token = token(key, JWSAlgorithm.RS256, ISSUER, Instant.now().minusSeconds(120));
        assertThrows(JwtException.class, () -> decoder.decode(token));
    }

    @Test
    void rejectsInvalidSignature() throws Exception {
        RSAKey otherKey = new RSAKeyGenerator(2048).keyID("test-key").generate();
        String token = token(otherKey, JWSAlgorithm.RS256, ISSUER, Instant.now().plusSeconds(300));
        assertThrows(JwtException.class, () -> decoder.decode(token));
    }

    @Test
    void rejectsOtherAlgorithms() throws Exception {
        String token = token(key, JWSAlgorithm.RS512, ISSUER, Instant.now().plusSeconds(300));
        assertThrows(JwtException.class, () -> decoder.decode(token));
    }

    private String token(RSAKey signingKey, JWSAlgorithm algorithm, String issuer, Instant expiresAt)
            throws Exception {
        var claims = new JWTClaimsSet.Builder().issuer(issuer).subject(SUBJECT)
                .issueTime(Date.from(Instant.now().minusSeconds(300)))
                .expirationTime(Date.from(expiresAt)).build();
        var token = new SignedJWT(new JWSHeader.Builder(algorithm).keyID(signingKey.getKeyID()).build(), claims);
        token.sign(new RSASSASigner(signingKey));
        return token.serialize();
    }
}
