package com.fraudetection.account_service.dto.response;

import java.math.BigDecimal;
import java.util.UUID;

public record BalanceResponse(
        String name,
        UUID accountId,
        BigDecimal balance,
        BigDecimal lockedBalance,
        BigDecimal availableBalance,
        String email,
        String cpf
) {
    public BalanceResponse(String name, UUID accountId, BigDecimal balance, BigDecimal lockedBalance, BigDecimal availableBalance) {
        this(name, accountId, balance, lockedBalance, availableBalance, null, null);
    }
}
