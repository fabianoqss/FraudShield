package com.fraudetection.account_service.dto;

import java.math.BigDecimal;
import java.util.UUID;

public record BalanceResponse(
        UUID accountId,
        BigDecimal balance,
        BigDecimal lockedBalance,
        BigDecimal availableBalance
) {
}
