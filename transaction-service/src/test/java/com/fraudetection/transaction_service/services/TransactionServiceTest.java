package com.fraudetection.transaction_service.services;

import com.fraudetection.transaction_service.clients.AccountServiceClient;
import com.fraudetection.transaction_service.entities.Transaction;
import com.fraudetection.transaction_service.kafka.producers.TransactionCreatedProducer;
import com.fraudetection.transaction_service.repositories.TransactionRepository;
import com.fraudetection.transaction_service.services.exceptions.AccountServiceUnavailableException;
import com.fraudetection.transaction_service.services.exceptions.TransactionNotFoundException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class TransactionServiceTest {

    private final TransactionRepository transactionRepository = mock(TransactionRepository.class);
    private final AccountServiceClient accountServiceClient = mock(AccountServiceClient.class);
    private final TransactionService transactionService = new TransactionService(
            transactionRepository, mock(TransactionCreatedProducer.class), accountServiceClient);

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
}
