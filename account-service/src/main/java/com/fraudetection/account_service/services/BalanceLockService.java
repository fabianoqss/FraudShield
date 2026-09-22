package com.fraudetection.account_service.services;

import com.fraudetection.account_service.dto.event.TransactionApprovedPayload;
import com.fraudetection.account_service.dto.event.TransactionCreatedPayload;
import com.fraudetection.account_service.dto.event.TransactionDeniedPayload;
import com.fraudetection.account_service.entities.BalanceLock;
import com.fraudetection.account_service.repositories.AccountRepository;
import com.fraudetection.account_service.repositories.BalanceLockRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class BalanceLockService {

    private final AccountRepository accountRepository;
    private final BalanceLockRepository balanceLockRepository;

    @Transactional
    public void createLock(TransactionCreatedPayload payload) {
        if (balanceLockRepository.findByTransactionId(payload.transactionId()).isPresent()) {
            log.warn("Balance lock already exists for transaction {}, skipping", payload.transactionId());
            return;
        }

        BalanceLock lock = newLock(payload.transactionId(), payload.sourceAccountId(), payload.amount());

        if (accountRepository.reserveFunds(payload.sourceAccountId(), payload.amount()) == 0) {
            log.warn("Insufficient available balance to reserve {} on account {} for transaction {}",
                    payload.amount(), payload.sourceAccountId(), payload.transactionId());
            lock.setAmount(BigDecimal.ZERO);
        }

        balanceLockRepository.save(lock);
    }

    @Transactional
    public void applyApproval(TransactionApprovedPayload payload) {
        BalanceLock lock = balanceLockRepository.findByTransactionId(payload.transactionId())
                .orElseGet(() -> newLock(payload.transactionId(), payload.sourceAccountId(), BigDecimal.ZERO));

        if (lock.isSettled()) {
            log.warn("Transaction {} was already settled, ignoring approval", payload.transactionId());
            return;
        }
        lock.setSettled(true);
        balanceLockRepository.save(lock);

        BigDecimal reserved = lock.getAmount();

        if (!accountRepository.existsById(payload.destinationAccountId())) {
            releaseReserved(payload.sourceAccountId(), reserved);
            log.error("Destination account {} not found for approved transaction {}, transfer not applied",
                    payload.destinationAccountId(), payload.transactionId());
            return;
        }

        boolean debited;
        if (reserved.compareTo(payload.amount()) == 0) {
            accountRepository.debitAndReleaseLock(payload.sourceAccountId(), payload.amount(), reserved);
            debited = true;
        } else {
            releaseReserved(payload.sourceAccountId(), reserved);
            debited = accountRepository.debitIfAvailable(payload.sourceAccountId(), payload.amount()) == 1;
        }

        if (!debited) {
            log.error("Insufficient available balance on account {} to settle approved transaction {}, transfer not applied",
                    payload.sourceAccountId(), payload.transactionId());
            return;
        }

        accountRepository.creditBalance(payload.destinationAccountId(), payload.amount());
    }

    @Transactional
    public void releaseOnDenial(TransactionDeniedPayload payload) {
        BalanceLock lock = balanceLockRepository.findByTransactionId(payload.transactionId())
                .orElseGet(() -> newLock(payload.transactionId(), payload.sourceAccountId(), BigDecimal.ZERO));

        if (lock.isSettled()) {
            log.warn("Transaction {} was already settled, ignoring denial", payload.transactionId());
            return;
        }
        lock.setSettled(true);
        balanceLockRepository.save(lock);

        releaseReserved(lock.getAccountId(), lock.getAmount());
    }

    private void releaseReserved(UUID accountId, BigDecimal reserved) {
        if (reserved.signum() > 0) {
            accountRepository.decreaseLockedBalance(accountId, reserved);
        }
    }

    private BalanceLock newLock(UUID transactionId, UUID accountId, BigDecimal amount) {
        BalanceLock lock = new BalanceLock();
        lock.setTransactionId(transactionId);
        lock.setAccountId(accountId);
        lock.setAmount(amount);
        return lock;
    }
}
