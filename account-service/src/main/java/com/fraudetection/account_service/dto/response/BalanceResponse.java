package com.fraudetection.account_service.dto.response;

import java.math.BigDecimal;
import java.util.UUID;

public record BalanceResponse(
        String name,
        UUID accountId,
        BigDecimal balance,
        BigDecimal lockedBalance,
        BigDecimal availableBalance
) {
}
