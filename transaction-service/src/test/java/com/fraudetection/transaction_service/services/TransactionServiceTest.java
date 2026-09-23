package com.fraudetection.transaction_service.services;

import com.fraudetection.transaction_service.clients.AccountServiceClient;
import com.fraudetection.transaction_service.dto.request.TransactionRequest;
import com.fraudetection.transaction_service.enums.PaymentStatus;
import com.fraudetection.transaction_service.enums.PaymentType;
import com.fraudetection.transaction_service.entities.Transaction;
import com.fraudetection.transaction_service.kafka.producers.TransactionCreatedProducer;
import com.fraudetection.transaction_service.repositories.TransactionRepository;
import com.fraudetection.transaction_service.services.exceptions.AccountServiceUnavailableException;
import com.fraudetection.transaction_service.services.exceptions.DestinationAccountNotFoundException;
import com.fraudetection.transaction_service.services.exceptions.InsufficientFundsException;
import com.fraudetection.transaction_service.services.exceptions.TransactionNotFoundException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class TransactionServiceTest {

    private final TransactionRepository transactionRepository = mock(TransactionRepository.class);
    private final AccountServiceClient accountServiceClient = mock(AccountServiceClient.class);
    private final TransactionCreatedProducer producer = mock(TransactionCreatedProducer.class);
    private final TransactionService transactionService = new TransactionService(
            transactionRepository, producer, accountServiceClient);

    private final UUID sourceAccountId = UUID.randomUUID();
    private final UUID destinationAccountId = UUID.randomUUID();
    private Transaction transaction;

    @BeforeEach
    void setUp() {
        transaction = new Transaction();
        transaction.setId(UUID.randomUUID());
        transaction.setSourceAccountId(sourceAccountId);
        transaction.setDestinationAccountId(destinationAccountId);
        transaction.setAmount(BigDecimal.TEN);
        when(transactionRepository.findById(transaction.getId())).thenReturn(Optional.of(transaction));
    }

    @Test
    void sourceOwnerCanReadTransaction() {
        when(accountServiceClient.ownsAccount(sourceAccountId)).thenReturn(true);

        assertThat(transactionService.getTransaction(transaction.getId()).id()).isEqualTo(transaction.getId());
    }

    @Test
    void destinationOwnerCanReadTransaction() {
        when(accountServiceClient.ownsAccount(sourceAccountId)).thenReturn(false);
        when(accountServiceClient.ownsAccount(destinationAccountId)).thenReturn(true);

        assertThat(transactionService.getTransaction(transaction.getId()).id()).isEqualTo(transaction.getId());
    }

    @Test
    void unrelatedUserGetsNotFound() {
        when(accountServiceClient.ownsAccount(sourceAccountId)).thenReturn(false);
        when(accountServiceClient.ownsAccount(destinationAccountId)).thenReturn(false);

        assertThatThrownBy(() -> transactionService.getTransaction(transaction.getId()))
                .isInstanceOf(TransactionNotFoundException.class);
    }

    @Test
    void accountServiceOutagePropagates() {
        when(accountServiceClient.ownsAccount(sourceAccountId)).thenThrow(new AccountServiceUnavailableException());

        assertThatThrownBy(() -> transactionService.getTransaction(transaction.getId()))
                .isInstanceOf(AccountServiceUnavailableException.class);
    }

    @Test
    void createRejectsAmountAboveAvailableBalance() {
        when(accountServiceClient.getOwnedAvailableBalance(sourceAccountId)).thenReturn(new BigDecimal("50.00"));

        assertThatThrownBy(() -> transactionService.createTransaction(request(new BigDecimal("50.01"))))
                .isInstanceOf(InsufficientFundsException.class);
        verify(transactionRepository, never()).save(any());
        verify(producer, never()).publish(any());
    }

    @Test
    void createRejectsUnknownDestination() {
        when(accountServiceClient.getOwnedAvailableBalance(sourceAccountId)).thenReturn(new BigDecimal("100"));
        when(accountServiceClient.accountExists(destinationAccountId)).thenReturn(false);

        assertThatThrownBy(() -> transactionService.createTransaction(request(BigDecimal.TEN)))
                .isInstanceOf(DestinationAccountNotFoundException.class);
        verify(producer, never()).publish(any());
    }

    @Test
    void createPublishesWhenFundsAndDestinationAreValid() {
        when(accountServiceClient.getOwnedAvailableBalance(sourceAccountId)).thenReturn(new BigDecimal("10"));
        when(accountServiceClient.accountExists(destinationAccountId)).thenReturn(true);

        transactionService.createTransaction(request(BigDecimal.TEN));

        verify(producer).publish(any());
    }

    @Test
    void outcomeMovesCreatedTransactionToFinalStatus() {
        when(transactionRepository.updateStatusIf(transaction.getId(), PaymentStatus.CREATED, PaymentStatus.APPROVED))
                .thenReturn(1);

        transactionService.applyOutcome(transaction.getId(), PaymentStatus.APPROVED);

        verify(transactionRepository).updateStatusIf(transaction.getId(), PaymentStatus.CREATED, PaymentStatus.APPROVED);
        verify(transactionRepository, never()).existsById(any());
    }

    @Test
    void redeliveredOutcomeDoesNotOverwriteFinalStatus() {
        when(transactionRepository.updateStatusIf(transaction.getId(), PaymentStatus.CREATED, PaymentStatus.DENIED))
                .thenReturn(0);
        when(transactionRepository.existsById(transaction.getId())).thenReturn(true);

        transactionService.applyOutcome(transaction.getId(), PaymentStatus.DENIED);

        verify(transactionRepository, never()).save(any());
    }

    private TransactionRequest request(BigDecimal amount) {
        return new TransactionRequest(sourceAccountId, destinationAccountId, amount, PaymentType.PIX,
                "device", "8.8.8.8", UUID.randomUUID().toString());
    }
}
