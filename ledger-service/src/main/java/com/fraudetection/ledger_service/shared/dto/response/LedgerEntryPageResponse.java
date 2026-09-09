package com.fraudetection.ledger_service.shared.dto.response;

import java.util.List;

public record LedgerEntryPageResponse(
        List<LedgerEntryResponse> content,
        int pageNumber,
        int pageSize,
        long totalElements,
        int totalPages
) {
}
