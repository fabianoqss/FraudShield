package com.fraudetection.account_service.services;

import com.fraudetection.account_service.dto.event.TransactionApprovedPayload;
import com.fraudetection.account_service.dto.event.TransactionCreatedPayload;
import com.fraudetection.account_service.dto.event.TransactionDeniedPayload;
import com.fraudetection.account_service.entities.BalanceLock;
import com.fraudetection.account_service.repositories.AccountRepository;
import com.fraudetection.account_service.repositories.BalanceLockRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.Optional;

@Slf4j
@Service
@RequiredArgsConstructor
public class BalanceLockService {

    private final AccountRepository accountRepository;
    private final BalanceLockRepository balanceLockRepository;

    @Transactional
    public void createLock(TransactionCreatedPayload payload) {
        BalanceLock lock = new BalanceLock();
        lock.setAccountId(payload.sourceAccountId());
        lock.setTransactionId(payload.transactionId());
        lock.setAmount(payload.amount());

        try {
            balanceLockRepository.save(lock);
        } catch (DataIntegrityViolationException e) {
            log.warn("Balance lock already exists for transaction {}, skipping", payload.transactionId());
            return;
        }

        accountRepository.increaseLockedBalance(payload.sourceAccountId(), payload.amount());
    }

    @Transactional
    public void applyApproval(TransactionApprovedPayload payload) {
        Optional<BalanceLock> lock = balanceLockRepository.findByTransactionId(payload.transactionId());

        BigDecimal lockAmount = BigDecimal.ZERO;
        if (lock.isPresent()) {
            lockAmount = lock.get().getAmount();
            if (lockAmount.compareTo(payload.amount()) != 0) {
                log.warn("Lock amount {} differs from approved amount {} for transaction {}",
                        lockAmount, payload.amount(), payload.transactionId());
            }
            balanceLockRepository.delete(lock.get());
        } else {
            log.warn("No balance lock found for approved transaction {}, debiting directly", payload.transactionId());
        }

        accountRepository.debitAndReleaseLock(payload.sourceAccountId(), payload.amount(), lockAmount);
        accountRepository.creditBalance(payload.destinationAccountId(), payload.amount());
    }

    @Transactional
    public void releaseOnDenial(TransactionDeniedPayload payload) {
        balanceLockRepository.findByTransactionId(payload.transactionId()).ifPresentOrElse(
                lock -> {
                    balanceLockRepository.delete(lock);
                    accountRepository.decreaseLockedBalance(lock.getAccountId(), lock.getAmount());
                },
                () -> log.warn("No balance lock found for denied transaction {}, nothing to release", payload.transactionId())
        );
    }
}
