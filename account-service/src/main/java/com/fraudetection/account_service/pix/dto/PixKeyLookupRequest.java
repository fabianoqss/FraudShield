package com.fraudetection.account_service.pix.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;

public record PixKeyLookupRequest(
        @Schema(description = "Recipient's PIX key: a CPF (11 digits, dots and dash optional), an e-mail "
                + "or a random key (UUID).", example = "529.982.247-25")
        @NotBlank(message = "PIX key is required")
        String key
) {
}
