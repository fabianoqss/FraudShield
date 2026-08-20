package com.fraudetection.auth_service.dto.response;

import java.util.UUID;

public record UserLookupResponse(
        UUID userId,
        String fullName,
        String email,
        String cpf
) {
}
