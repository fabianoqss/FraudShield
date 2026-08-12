package com.fraudetection.transaction_service.dto.response;

import java.math.BigDecimal;
import java.util.UUID;

public record TransactionResponse(
        UUID id,
        UUID sourceAccountId,
        UUID destinationAccountId,
        BigDecimal amount

) {
}
