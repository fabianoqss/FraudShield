package com.fraudetection.auth_service.dto.response;

public record ServiceTokenResponse(
        String accessToken,
        String tokenType,
        long expiresIn
) {
}
