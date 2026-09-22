package com.fraudetection.auth_service.dto.request;

import jakarta.validation.constraints.NotBlank;

public record ServiceTokenRequest(
        @NotBlank(message = "Client id is required")
        String clientId,

        @NotBlank(message = "Client secret is required")
        String clientSecret
) {
}
