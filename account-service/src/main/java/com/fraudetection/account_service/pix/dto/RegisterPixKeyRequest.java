package com.fraudetection.account_service.pix.dto;

import com.fraudetection.account_service.pix.PixKeyType;
import jakarta.validation.constraints.NotNull;

public record RegisterPixKeyRequest(
        @NotNull(message = "PIX key type is required")
        PixKeyType type
) {
}
