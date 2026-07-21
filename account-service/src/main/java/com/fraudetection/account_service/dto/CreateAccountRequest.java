package com.fraudetection.account_service.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.util.UUID;

public record CreateAccountRequest(
        @NotNull(message = "Owner id is required")
        UUID ownerId,

        @NotBlank(message = "Owner name is required")
        String ownerName
) {
}
