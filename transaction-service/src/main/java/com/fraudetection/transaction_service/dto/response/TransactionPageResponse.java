package com.fraudetection.transaction_service.dto.response;

import java.util.List;

public record TransactionPageResponse(
        List<TransactionResponse> transactions,
        int page,
        int size,
        long totalElements,
        int totalPages
) {
}
