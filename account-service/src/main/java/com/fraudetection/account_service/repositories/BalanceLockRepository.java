package com.fraudetection.account_service.repositories;

import com.fraudetection.account_service.entities.BalanceLock;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;
import java.util.UUID;

public interface BalanceLockRepository extends JpaRepository<BalanceLock, UUID> {

    Optional<BalanceLock> findByTransactionId(UUID transactionId);

    /**
     * Marks the lock settled only if it was not settled yet. The database row lock makes this atomic: of two
     * concurrent claims, the second waits for the first to commit, re-checks {@code settled = false} and updates
     * nothing. Returns 1 for the caller that owns the settlement, 0 otherwise.
     */
    @Modifying
    @Query("update BalanceLock b set b.settled = true where b.transactionId = :transactionId and b.settled = false")
    int claimSettlement(@Param("transactionId") UUID transactionId);
}
