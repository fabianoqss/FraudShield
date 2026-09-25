package com.fraudetection.ledger_service.controllers;

import com.fraudetection.ledger_service.clients.AccountServiceClient;
import com.fraudetection.ledger_service.documents.LedgerEntry;
import com.fraudetection.ledger_service.dto.response.LedgerEntryPageResponse;
import com.fraudetection.ledger_service.dto.response.LedgerEntryResponse;
import com.fraudetection.ledger_service.services.LedgerQueryService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/ledger")
@RequiredArgsConstructor
public class LedgerController implements LedgerApi {

    private final AccountServiceClient accountServiceClient;
    private final LedgerQueryService ledgerQueryService;

    @Override
    @GetMapping("/account/{id}")
    public ResponseEntity<LedgerEntryPageResponse> getAccountLedger(
            @PathVariable UUID id,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        accountServiceClient.verifyOwnership(id);

        Page<LedgerEntry> result = ledgerQueryService.getEntriesForAccount(id, page, size);
        List<LedgerEntryResponse> entries = result.getContent().stream()
                .map(LedgerEntryResponse::from)
                .toList();

        return ResponseEntity.ok(new LedgerEntryPageResponse(
                entries,
                result.getNumber(),
                result.getSize(),
                result.getTotalElements(),
                result.getTotalPages()
        ));
    }
}
