package com.fraudetection.account_service.repositories;

import com.fraudetection.account_service.entities.BalanceLock;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface BalanceLockRepository extends JpaRepository<BalanceLock, UUID> {

    Optional<BalanceLock> findByTransactionId(UUID transactionId);
}
