package com.fraudetection.ledger_service.api.controller;

import com.fraudetection.ledger_service.application.service.LedgerService;
import com.fraudetection.ledger_service.infrastructure.client.AccountServiceClient;
import com.fraudetection.ledger_service.shared.dto.response.LedgerEntryPageResponse;
import com.fraudetection.ledger_service.shared.dto.response.LedgerEntryResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/ledger")
@RequiredArgsConstructor
public class LedgerController {

    private final AccountServiceClient accountServiceClient;
    private final LedgerService ledgerService;

    @GetMapping("/account/{id}")
    public ResponseEntity<LedgerEntryPageResponse> getAccountLedger(
            @PathVariable UUID id,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            Authentication authentication
    ) {
        UUID requestingUserId = UUID.fromString(authentication.getName());
        accountServiceClient.verifyAccountOwnership(id, requestingUserId);

        Page<LedgerEntryResponse> pageResult = ledgerService.getAccountLedger(id, page, size);
        return ResponseEntity.ok(new LedgerEntryPageResponse(
                pageResult.getContent(),
                pageResult.getNumber(),
                pageResult.getSize(),
                pageResult.getTotalElements(),
                pageResult.getTotalPages()
        ));
    }
}
