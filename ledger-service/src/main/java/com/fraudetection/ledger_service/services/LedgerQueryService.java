package com.fraudetection.ledger_service.services;

import com.fraudetection.ledger_service.documents.LedgerEntry;
import com.fraudetection.ledger_service.repositories.LedgerEntryRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;

import java.util.UUID;

@Service
@RequiredArgsConstructor
public class LedgerQueryService {

    private final LedgerEntryRepository ledgerEntryRepository;

    public Page<LedgerEntry> getEntriesForAccount(UUID accountId, int page, int size) {
        PageRequest pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "recordedAt"));
        return ledgerEntryRepository.findByAccountId(accountId.toString(), pageable);
    }
}
