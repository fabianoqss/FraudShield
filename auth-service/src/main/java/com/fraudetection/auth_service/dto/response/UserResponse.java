package com.fraudetection.auth_service.dto.response;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;

public record UserResponse(
        UUID id,
        String email,
        String fullname,
        LocalDate birthDate,
        LocalDateTime createdAt
) {
}
