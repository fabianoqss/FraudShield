package com.fraudetection.transaction_service.repositories;

import com.fraudetection.transaction_service.entities.Transaction;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface TransactionRepository extends JpaRepository<Transaction, UUID> {

}
