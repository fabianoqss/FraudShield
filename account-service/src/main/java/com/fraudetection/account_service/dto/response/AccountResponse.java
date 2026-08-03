package com.fraudetection.account_service.dto.response;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

public record AccountResponse(
        UUID id,
        UUID ownerId,
        String ownerName,
        BigDecimal balance,
        BigDecimal lockedBalance,
        String status,
        LocalDateTime createdAt
) {
}
