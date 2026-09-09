package com.fraudetection.ledger_service.domain.repository;

import com.fraudetection.ledger_service.domain.model.LedgerEntry;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.data.mongodb.repository.Query;

import java.util.UUID;

public interface LedgerEntryRepository extends MongoRepository<LedgerEntry, UUID> {

    @Query("{ '$or': [ { 'eventPayload.sourceAccountId': ?0 }, { 'eventPayload.destinationAccountId': ?0 } ] }")
    Page<LedgerEntry> findByAccountId(String accountId, Pageable pageable);
}
