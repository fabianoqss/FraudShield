package com.fraudetection.account_service.repositories;

import com.fraudetection.account_service.entities.Account;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface AccountRepository extends JpaRepository<Account, UUID> {

    Optional<Account> findFirstByOwnerId(UUID ownerId);

    List<Account> findByOwnerIdOrderByCreatedAtAsc(UUID ownerId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select a from Account a where a.id = :id")
    Optional<Account> findByIdForUpdate(@Param("id") UUID id);

    @Transactional
    @Modifying
    @Query("update Account a set a.lockedBalance = a.lockedBalance + :amount "
            + "where a.id = :accountId and a.balance - a.lockedBalance >= :amount")
    int reserveFunds(@Param("accountId") UUID accountId, @Param("amount") BigDecimal amount);

    @Transactional
    @Modifying
    @Query("update Account a set a.balance = a.balance - :amount "
            + "where a.id = :accountId and a.balance - a.lockedBalance >= :amount")
    int debitIfAvailable(@Param("accountId") UUID accountId, @Param("amount") BigDecimal amount);

    @Transactional
    @Modifying
    @Query("update Account a set a.balance = a.balance - :amount, a.lockedBalance = a.lockedBalance - :lockAmount where a.id = :accountId")
    void debitAndReleaseLock(@Param("accountId") UUID accountId, @Param("amount") BigDecimal amount, @Param("lockAmount") BigDecimal lockAmount);

    @Transactional
    @Modifying
    @Query("update Account a set a.balance = a.balance + :amount where a.id = :accountId")
    void creditBalance(@Param("accountId") UUID accountId, @Param("amount") BigDecimal amount);

    @Transactional
    @Modifying
    @Query("update Account a set a.lockedBalance = a.lockedBalance - :amount where a.id = :accountId")
    void decreaseLockedBalance(@Param("accountId") UUID accountId, @Param("amount") BigDecimal amount);
}
