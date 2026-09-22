package com.fraudetection.auth_service.security;

import com.nimbusds.jose.JOSEException;
import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jose.jwk.source.ImmutableJWKSet;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtValidators;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;

import java.security.KeyFactory;
import java.security.KeyPairGenerator;
import java.security.NoSuchAlgorithmException;
import java.security.interfaces.RSAPrivateCrtKey;
import java.security.interfaces.RSAPublicKey;
import java.security.spec.InvalidKeySpecException;
import java.security.spec.PKCS8EncodedKeySpec;
import java.security.spec.RSAPublicKeySpec;
import java.util.Base64;

@Slf4j
@Configuration
public class JwtKeyConfig {

    @Bean
    public RSAKey rsaKey(@Value("${jwt.private-key:}") String privateKey) throws JOSEException {
        RSAPrivateCrtKey rsaPrivateKey = privateKey.isBlank() ? generateEphemeralKey() : parsePrivateKey(privateKey);
        RSAPublicKey rsaPublicKey = derivePublicKey(rsaPrivateKey);

        return new RSAKey.Builder(rsaPublicKey)
                .privateKey(rsaPrivateKey)
                .keyIDFromThumbprint()
                .build();
    }

    @Bean
    public JWKSet publicJwkSet(RSAKey rsaKey) {
        return new JWKSet(rsaKey.toPublicJWK());
    }

    @Bean
    public JwtEncoder jwtEncoder(RSAKey rsaKey) {
        return new NimbusJwtEncoder(new ImmutableJWKSet<>(new JWKSet(rsaKey)));
    }

    @Bean
    public JwtDecoder jwtDecoder(RSAKey rsaKey, @Value("${jwt.issuer}") String issuer) throws JOSEException {
        NimbusJwtDecoder decoder = NimbusJwtDecoder.withPublicKey(rsaKey.toRSAPublicKey()).build();
        decoder.setJwtValidator(JwtValidators.createDefaultWithIssuer(issuer));
        return decoder;
    }

    private RSAPrivateCrtKey parsePrivateKey(String base64Der) {
        try {
            byte[] der = Base64.getDecoder().decode(base64Der.strip());
            return (RSAPrivateCrtKey) KeyFactory.getInstance("RSA").generatePrivate(new PKCS8EncodedKeySpec(der));
        } catch (IllegalArgumentException | NoSuchAlgorithmException | InvalidKeySpecException | ClassCastException e) {
            throw new IllegalStateException("jwt.private-key must be a base64-encoded PKCS#8 DER RSA private key", e);
        }
    }

    private RSAPublicKey derivePublicKey(RSAPrivateCrtKey privateKey) {
        try {
            RSAPublicKeySpec spec = new RSAPublicKeySpec(privateKey.getModulus(), privateKey.getPublicExponent());
            return (RSAPublicKey) KeyFactory.getInstance("RSA").generatePublic(spec);
        } catch (NoSuchAlgorithmException | InvalidKeySpecException e) {
            throw new IllegalStateException("Could not derive the RSA public key", e);
        }
    }

    private RSAPrivateCrtKey generateEphemeralKey() {
        log.warn("jwt.private-key is not set; generating an ephemeral RSA key. "
                + "Tokens will be invalidated on restart. Set JWT_PRIVATE_KEY outside local development.");
        try {
            KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
            generator.initialize(2048);
            return (RSAPrivateCrtKey) generator.generateKeyPair().getPrivate();
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("RSA is not available", e);
        }
    }
}
