package com.fraudetection.account_service.services;

import com.fraudetection.account_service.AbstractIntegrationTest;
import com.fraudetection.account_service.dto.event.TransactionApprovedPayload;
import com.fraudetection.account_service.dto.event.TransactionCreatedPayload;
import com.fraudetection.account_service.dto.event.TransactionDeniedPayload;
import com.fraudetection.account_service.entities.Account;
import com.fraudetection.account_service.repositories.AccountRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CyclicBarrier;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Two consumers handling outcomes of the same transaction at once (a rebalance redelivery, two replicas, or an
 * approval and a denial on different threads) must settle it exactly once.
 */
class BalanceLockConcurrencyTest extends AbstractIntegrationTest {

    private static final int ROUNDS = 20;
    private static final BigDecimal AMOUNT = new BigDecimal("10.00");
    private static final BigDecimal INITIAL = new BigDecimal("1000.00");

    @Autowired
    private BalanceLockService balanceLockService;

    @Autowired
    private AccountRepository accountRepository;

    @Test
    void concurrentApprovalsOfTheSameTransactionMoveMoneyOnce() {
        UUID source = account(INITIAL);
        UUID destination = account(BigDecimal.ZERO);

        for (int round = 0; round < ROUNDS; round++) {
            UUID transactionId = UUID.randomUUID();
            balanceLockService.createLock(new TransactionCreatedPayload(transactionId, source, AMOUNT));
            TransactionApprovedPayload approved = new TransactionApprovedPayload(transactionId, source, destination, AMOUNT);

            runTogether(() -> balanceLockService.applyApproval(approved),
                    () -> balanceLockService.applyApproval(approved));
        }

        BigDecimal moved = AMOUNT.multiply(BigDecimal.valueOf(ROUNDS));
        assertBalances(source, INITIAL.subtract(moved), BigDecimal.ZERO);
        assertBalances(destination, moved, BigDecimal.ZERO);
    }

    @Test
    void anApprovalRacingADenialSettlesTheTransactionOnce() {
        UUID source = account(INITIAL);
        UUID destination = account(BigDecimal.ZERO);

        for (int round = 0; round < ROUNDS; round++) {
            UUID transactionId = UUID.randomUUID();
            balanceLockService.createLock(new TransactionCreatedPayload(transactionId, source, AMOUNT));

            runTogether(
                    () -> balanceLockService.applyApproval(
                            new TransactionApprovedPayload(transactionId, source, destination, AMOUNT)),
                    () -> balanceLockService.releaseOnDenial(new TransactionDeniedPayload(transactionId, source)));
        }

        Account sourceAccount = accountRepository.findById(source).orElseThrow();
        Account destinationAccount = accountRepository.findById(destination).orElseThrow();
        // Whichever outcome won each round, no money was created or lost and nothing stayed reserved.
        assertThat(sourceAccount.getLockedBalance()).isEqualByComparingTo(BigDecimal.ZERO);
        assertThat(sourceAccount.getBalance().add(destinationAccount.getBalance())).isEqualByComparingTo(INITIAL);
    }

    private UUID account(BigDecimal balance) {
        Account account = new Account();
        account.setOwnerId(UUID.randomUUID());
        account.setOwnerName("Concurrency Test");
        account.setBalance(balance);
        return accountRepository.save(account).getId();
    }

    private void assertBalances(UUID accountId, BigDecimal balance, BigDecimal locked) {
        Account account = accountRepository.findById(accountId).orElseThrow();
        assertThat(account.getBalance()).isEqualByComparingTo(balance);
        assertThat(account.getLockedBalance()).isEqualByComparingTo(locked);
    }

    private static void runTogether(Runnable first, Runnable second) {
        CyclicBarrier barrier = new CyclicBarrier(2);
        List<CompletableFuture<Void>> runs = List.of(first, second).stream()
                .map(task -> CompletableFuture.runAsync(() -> {
                    await(barrier);
                    task.run();
                }))
                .toList();
        CompletableFuture.allOf(runs.toArray(CompletableFuture[]::new)).join();
    }

    private static void await(CyclicBarrier barrier) {
        try {
            barrier.await();
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }
}
