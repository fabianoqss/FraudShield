package com.fraudetection.auth_service.controllers;

import com.nimbusds.jose.jwk.JWKSet;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequiredArgsConstructor
public class JwksController {

    private final JWKSet publicJwkSet;

    @GetMapping("/.well-known/jwks.json")
    public Map<String, Object> jwks() {
        return publicJwkSet.toJSONObject();
    }
}
