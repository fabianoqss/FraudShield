package com.fraudetection.auth_service.dto;

import java.time.LocalDateTime;
import java.util.UUID;

public record UserResponse(
        UUID id,
        String email,
        String fullname,
        LocalDateTime createdAt
) {
}
