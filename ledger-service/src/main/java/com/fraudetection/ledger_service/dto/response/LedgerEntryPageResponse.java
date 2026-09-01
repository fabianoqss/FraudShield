package com.fraudetection.ledger_service.dto.response;

import java.util.List;

public record LedgerEntryPageResponse(
        List<LedgerEntryResponse> entries,
        int page,
        int size,
        long totalElements,
        int totalPages
) {
}
