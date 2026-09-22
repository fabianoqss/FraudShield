package com.fraudetection.account_service.services;

import com.fraudetection.account_service.dto.event.TransactionApprovedPayload;
import com.fraudetection.account_service.dto.event.TransactionCreatedPayload;
import com.fraudetection.account_service.dto.event.TransactionDeniedPayload;
import com.fraudetection.account_service.entities.BalanceLock;
import com.fraudetection.account_service.repositories.AccountRepository;
import com.fraudetection.account_service.repositories.BalanceLockRepository;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.math.BigDecimal;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class BalanceLockServiceTest {

    private static final BigDecimal AMOUNT = new BigDecimal("120");

    private final AccountRepository accountRepository = mock(AccountRepository.class);
    private final BalanceLockRepository balanceLockRepository = mock(BalanceLockRepository.class);
    private final BalanceLockService service = new BalanceLockService(accountRepository, balanceLockRepository);

    private final UUID transactionId = UUID.randomUUID();
    private final UUID source = UUID.randomUUID();
    private final UUID destination = UUID.randomUUID();

    @Test
    void createLockReservesFundsWhenAvailable() {
        when(accountRepository.reserveFunds(source, AMOUNT)).thenReturn(1);

        service.createLock(new TransactionCreatedPayload(transactionId, source, AMOUNT));

        assertThat(savedLock().getAmount()).isEqualByComparingTo(AMOUNT);
    }

    @Test
    void createLockReservesNothingWhenBalanceIsInsufficient() {
        when(accountRepository.reserveFunds(source, AMOUNT)).thenReturn(0);

        service.createLock(new TransactionCreatedPayload(transactionId, source, AMOUNT));

        assertThat(savedLock().getAmount()).isEqualByComparingTo(BigDecimal.ZERO);
    }

    @Test
    void createLockAfterSettlementDoesNotReserveAgain() {
        when(balanceLockRepository.findByTransactionId(transactionId)).thenReturn(Optional.of(lock(BigDecimal.ZERO, true)));

        service.createLock(new TransactionCreatedPayload(transactionId, source, AMOUNT));

        verify(accountRepository, never()).reserveFunds(any(), any());
    }

    @Test
    void approvalWithReservedFundsDebitsAndCredits() {
        when(balanceLockRepository.findByTransactionId(transactionId)).thenReturn(Optional.of(lock(AMOUNT, false)));
        when(accountRepository.existsById(destination)).thenReturn(true);

        service.applyApproval(approved());

        verify(accountRepository).debitAndReleaseLock(source, AMOUNT, AMOUNT);
        verify(accountRepository).creditBalance(destination, AMOUNT);
    }

    @Test
    void approvalWithoutReservationNeverOverdraws() {
        when(balanceLockRepository.findByTransactionId(transactionId)).thenReturn(Optional.of(lock(BigDecimal.ZERO, false)));
        when(accountRepository.existsById(destination)).thenReturn(true);
        when(accountRepository.debitIfAvailable(source, AMOUNT)).thenReturn(0);

        service.applyApproval(approved());

        verify(accountRepository, never()).debitAndReleaseLock(any(), any(), any());
        verify(accountRepository, never()).creditBalance(any(), any());
    }

    @Test
    void approvalArrivingBeforeLockDebitsOnlyIfAvailableAndBlocksLateLock() {
        when(balanceLockRepository.findByTransactionId(transactionId)).thenReturn(Optional.empty());
        when(accountRepository.existsById(destination)).thenReturn(true);
        when(accountRepository.debitIfAvailable(source, AMOUNT)).thenReturn(1);

        service.applyApproval(approved());

        verify(accountRepository).creditBalance(destination, AMOUNT);
        BalanceLock marker = savedLock();
        assertThat(marker.isSettled()).isTrue();
        assertThat(marker.getTransactionId()).isEqualTo(transactionId);
    }

    @Test
    void duplicateApprovalIsIgnored() {
        when(balanceLockRepository.findByTransactionId(transactionId)).thenReturn(Optional.of(lock(AMOUNT, true)));

        service.applyApproval(approved());

        verify(accountRepository, never()).debitAndReleaseLock(any(), any(), any());
        verify(accountRepository, never()).debitIfAvailable(any(), any());
        verify(accountRepository, never()).creditBalance(any(), any());
    }

    @Test
    void approvalToMissingDestinationReleasesFundsWithoutDebiting() {
        when(balanceLockRepository.findByTransactionId(transactionId)).thenReturn(Optional.of(lock(AMOUNT, false)));
        when(accountRepository.existsById(destination)).thenReturn(false);

        service.applyApproval(approved());

        verify(accountRepository).decreaseLockedBalance(source, AMOUNT);
        verify(accountRepository, never()).debitAndReleaseLock(any(), any(), any());
        verify(accountRepository, never()).creditBalance(any(), any());
    }

    @Test
    void denialReleasesReservedFundsOnce() {
        BalanceLock lock = lock(AMOUNT, false);
        when(balanceLockRepository.findByTransactionId(transactionId)).thenReturn(Optional.of(lock));

        service.releaseOnDenial(new TransactionDeniedPayload(transactionId, source));
        service.releaseOnDenial(new TransactionDeniedPayload(transactionId, source));

        verify(accountRepository).decreaseLockedBalance(source, AMOUNT);
        assertThat(lock.isSettled()).isTrue();
    }

    private TransactionApprovedPayload approved() {
        return new TransactionApprovedPayload(transactionId, source, destination, AMOUNT);
    }

    private BalanceLock lock(BigDecimal amount, boolean settled) {
        BalanceLock lock = new BalanceLock();
        lock.setTransactionId(transactionId);
        lock.setAccountId(source);
        lock.setAmount(amount);
        lock.setSettled(settled);
        return lock;
    }

    private BalanceLock savedLock() {
        ArgumentCaptor<BalanceLock> captor = ArgumentCaptor.forClass(BalanceLock.class);
        verify(balanceLockRepository).save(captor.capture());
        return captor.getValue();
    }
}
