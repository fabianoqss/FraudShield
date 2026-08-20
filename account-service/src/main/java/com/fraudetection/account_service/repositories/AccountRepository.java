package com.fraudetection.account_service.repositories;

import com.fraudetection.account_service.entities.Account;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.math.BigDecimal;
import java.util.Optional;
import java.util.UUID;

public interface AccountRepository extends JpaRepository<Account, UUID> {

    Optional<Account> findFirstByOwnerId(UUID ownerId);

    @Modifying
    @Query("update Account a set a.lockedBalance = a.lockedBalance + :amount where a.id = :accountId")
    void increaseLockedBalance(@Param("accountId") UUID accountId, @Param("amount") BigDecimal amount);

    @Modifying
    @Query("update Account a set a.balance = a.balance - :amount, a.lockedBalance = a.lockedBalance - :lockAmount where a.id = :accountId")
    void debitAndReleaseLock(@Param("accountId") UUID accountId, @Param("amount") BigDecimal amount, @Param("lockAmount") BigDecimal lockAmount);

    @Modifying
    @Query("update Account a set a.balance = a.balance + :amount where a.id = :accountId")
    void creditBalance(@Param("accountId") UUID accountId, @Param("amount") BigDecimal amount);

    @Modifying
    @Query("update Account a set a.lockedBalance = a.lockedBalance - :amount where a.id = :accountId")
    void decreaseLockedBalance(@Param("accountId") UUID accountId, @Param("amount") BigDecimal amount);
}
