package com.fraudetection.account_service.pix.dto;

import jakarta.validation.constraints.NotBlank;

public record PixKeyLookupRequest(
        @NotBlank(message = "PIX key is required")
        String key
) {
}
