package com.fraudetection.transaction_service.repositories;

import com.fraudetection.transaction_service.entities.Transaction;
import com.fraudetection.transaction_service.enums.PaymentStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.UUID;

public interface TransactionRepository extends JpaRepository<Transaction, UUID> {

    boolean existsByIdempotencyKey(String idempotencyKey);

    Page<Transaction> findBySourceAccountIdOrDestinationAccountId(UUID sourceAccountId, UUID destinationAccountId,
                                                                  Pageable pageable);

    @Modifying
    @Query("update Transaction t set t.status = :newStatus where t.id = :id and t.status = :expectedStatus")
    int updateStatusIf(@Param("id") UUID id,
                       @Param("expectedStatus") PaymentStatus expectedStatus,
                       @Param("newStatus") PaymentStatus newStatus);

}
