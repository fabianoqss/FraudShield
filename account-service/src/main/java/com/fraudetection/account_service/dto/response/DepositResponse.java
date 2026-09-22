package com.fraudetection.account_service.dto.response;

import java.math.BigDecimal;

public record DepositResponse(
        String receiverName,
        BigDecimal amount
) {
}
