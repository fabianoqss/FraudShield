package com.fraudetection.auth_service.security;

import com.fraudetection.auth_service.entities.User;
import com.nimbusds.jose.jwk.RSAKey;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtException;

import java.util.Base64;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class JwtServiceTest {

    private static final String ISSUER = "fraudshield-auth-service";

    private final JwtKeyConfig jwtKeyConfig = new JwtKeyConfig();
    private RSAKey rsaKey;
    private JwtService jwtService;
    private JwtDecoder jwtDecoder;

    @BeforeEach
    void setUp() throws Exception {
        rsaKey = jwtKeyConfig.rsaKey("");
        jwtService = new JwtService(jwtKeyConfig.jwtEncoder(rsaKey), ISSUER, 900_000, 300_000);
        jwtDecoder = jwtKeyConfig.jwtDecoder(rsaKey, ISSUER);
    }

    @Test
    void userTokenIsSignedWithRs256AndCarriesIdentityClaims() {
        User user = new User();
        user.setId(UUID.randomUUID());
        user.setEmail("ana@example.com");
        user.setFullName("Ana Souza");

        Jwt jwt = jwtDecoder.decode(jwtService.generateToken(user));

        assertThat(jwt.getHeaders()).containsEntry("alg", "RS256").containsEntry("kid", rsaKey.getKeyID());
        assertThat(jwt.getSubject()).isEqualTo(user.getId().toString());
        assertThat(jwt.getClaimAsString("iss")).isEqualTo(ISSUER);
        assertThat(jwt.getClaimAsString("email")).isEqualTo("ana@example.com");
        assertThat(jwt.getClaimAsString("fullName")).isEqualTo("Ana Souza");
        assertThat(jwt.hasClaim("scope")).isFalse();
    }

    @Test
    void serviceTokenCarriesClientIdAndScopes() {
        Jwt jwt = jwtDecoder.decode(jwtService.generateServiceToken("account-service", List.of("users:lookup")));

        assertThat(jwt.getSubject()).isEqualTo("account-service");
        assertThat(jwt.getClaimAsString("scope")).isEqualTo("users:lookup");
    }

    @Test
    void decoderRejectsTokenFromAnotherIssuer() {
        JwtService foreignIssuer = new JwtService(jwtKeyConfig.jwtEncoder(rsaKey), "someone-else", 900_000, 300_000);
        String token = foreignIssuer.generateServiceToken("account-service", List.of());

        assertThatThrownBy(() -> jwtDecoder.decode(token)).isInstanceOf(JwtException.class);
    }

    @Test
    void decoderRejectsTokenSignedWithAnotherKey() throws Exception {
        RSAKey otherKey = jwtKeyConfig.rsaKey("");
        JwtService otherSigner = new JwtService(jwtKeyConfig.jwtEncoder(otherKey), ISSUER, 900_000, 300_000);
        String token = otherSigner.generateServiceToken("account-service", List.of());

        assertThatThrownBy(() -> jwtDecoder.decode(token)).isInstanceOf(JwtException.class);
    }

    @Test
    void configuredPrivateKeyIsLoadedFromBase64Pkcs8() throws Exception {
        String encoded = Base64.getEncoder().encodeToString(rsaKey.toRSAPrivateKey().getEncoded());

        RSAKey loaded = jwtKeyConfig.rsaKey(encoded);

        assertThat(loaded.getKeyID()).isEqualTo(rsaKey.getKeyID());
    }

    @Test
    void invalidConfiguredPrivateKeyFailsFast() {
        assertThatThrownBy(() -> jwtKeyConfig.rsaKey("not-a-key"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("jwt.private-key");
    }
}
