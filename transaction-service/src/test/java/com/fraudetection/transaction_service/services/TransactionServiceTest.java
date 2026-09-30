package com.fraudetection.transaction_service.services;

import com.fraudetection.transaction_service.clients.AccountServiceClient;
import com.fraudetection.transaction_service.dto.request.TransactionRequest;
import com.fraudetection.transaction_service.enums.PaymentStatus;
import com.fraudetection.transaction_service.enums.PaymentType;
import com.fraudetection.transaction_service.entities.Transaction;
import com.fraudetection.transaction_service.kafka.producers.TransactionCreatedProducer;
import com.fraudetection.transaction_service.repositories.TransactionRepository;
import com.fraudetection.transaction_service.services.exceptions.AccountAccessDeniedException;
import com.fraudetection.transaction_service.services.exceptions.AccountServiceUnavailableException;
import com.fraudetection.transaction_service.services.exceptions.InsufficientFundsException;
import com.fraudetection.transaction_service.services.exceptions.InvalidPixLookupException;
import com.fraudetection.transaction_service.services.exceptions.SameAccountTransferException;
import com.fraudetection.transaction_service.services.exceptions.TransactionNotFoundException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
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
    private final UUID lookupId = UUID.randomUUID();
    private Transaction transaction;

    @BeforeEach
    void setUp() {
        transaction = new Transaction();
        transaction.setId(UUID.randomUUID());
        transaction.setSourceAccountId(sourceAccountId);
        transaction.setDestinationAccountId(destinationAccountId);
        transaction.setAmount(BigDecimal.TEN);
        when(transactionRepository.findById(transaction.getId())).thenReturn(Optional.of(transaction));
        when(transactionRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
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

        assertThatThrownBy(() -> transactionService.createTransaction(request(new BigDecimal("50.01")), "203.0.113.5"))
                .isInstanceOf(InsufficientFundsException.class);
        verify(transactionRepository, never()).save(any());
        verify(producer, never()).publish(any());
    }

    @Test
    void createRejectsExpiredLookup() {
        when(accountServiceClient.getOwnedAvailableBalance(sourceAccountId)).thenReturn(new BigDecimal("100"));
        when(accountServiceClient.resolvePixLookup(lookupId)).thenThrow(new InvalidPixLookupException());

        assertThatThrownBy(() -> transactionService.createTransaction(request(BigDecimal.TEN), "203.0.113.5"))
                .isInstanceOf(InvalidPixLookupException.class);
        verify(transactionRepository, never()).save(any());
        verify(producer, never()).publish(any());
    }

    @Test
    void createRejectsTransferToTheSourceAccount() {
        when(accountServiceClient.getOwnedAvailableBalance(sourceAccountId)).thenReturn(new BigDecimal("100"));
        when(accountServiceClient.resolvePixLookup(lookupId)).thenReturn(sourceAccountId);

        assertThatThrownBy(() -> transactionService.createTransaction(request(BigDecimal.TEN), "203.0.113.5"))
                .isInstanceOf(SameAccountTransferException.class);
        verify(transactionRepository, never()).save(any());
    }

    @Test
    void createStoresTheResolvedDestination() {
        when(accountServiceClient.getOwnedAvailableBalance(sourceAccountId)).thenReturn(new BigDecimal("100"));
        when(accountServiceClient.resolvePixLookup(lookupId)).thenReturn(destinationAccountId);

        assertThat(transactionService.createTransaction(request(BigDecimal.TEN), "203.0.113.5").destinationAccountId())
                .isEqualTo(destinationAccountId);
    }

    @Test
    void createDoesNotResolveTheLookupWhenFundsAreInsufficient() {
        when(accountServiceClient.getOwnedAvailableBalance(sourceAccountId)).thenReturn(BigDecimal.ONE);

        assertThatThrownBy(() -> transactionService.createTransaction(request(BigDecimal.TEN), "203.0.113.5"))
                .isInstanceOf(InsufficientFundsException.class);
        verify(accountServiceClient, never()).resolvePixLookup(any());
    }

    @Test
    void createPublishesWhenFundsAndDestinationAreValid() {
        when(accountServiceClient.getOwnedAvailableBalance(sourceAccountId)).thenReturn(new BigDecimal("10"));
        when(accountServiceClient.resolvePixLookup(lookupId)).thenReturn(destinationAccountId);

        transactionService.createTransaction(request(BigDecimal.TEN), "203.0.113.5");

        InOrder inOrder = inOrder(transactionRepository, producer);
        ArgumentCaptor<Transaction> captor = ArgumentCaptor.forClass(Transaction.class);
        inOrder.verify(transactionRepository).save(captor.capture());
        assertThat(captor.getValue().getIpAddress()).isEqualTo("203.0.113.5");
        inOrder.verify(producer).publish(captor.getValue());
    }

    @Test
    void createDoesNotPublishWhenSaveFails() {
        when(accountServiceClient.getOwnedAvailableBalance(sourceAccountId)).thenReturn(new BigDecimal("10"));
        when(accountServiceClient.resolvePixLookup(lookupId)).thenReturn(destinationAccountId);
        when(transactionRepository.save(any())).thenThrow(new DataIntegrityViolationException("duplicate key"));

        assertThatThrownBy(() -> transactionService.createTransaction(request(BigDecimal.TEN), "203.0.113.5"))
                .isInstanceOf(DataIntegrityViolationException.class);
        verify(producer, never()).publish(any());
    }

    @Test
    void listReturnsTransactionsWhereAccountIsSourceOrDestinationNewestFirst() {
        Pageable pageable = listAndCapturePageable(0, 20);

        assertThat(pageable.getPageNumber()).isZero();
        assertThat(pageable.getPageSize()).isEqualTo(20);
        assertThat(pageable.getSort()).isEqualTo(Sort.by(Sort.Direction.DESC, "createdAt"));
    }

    @Test
    void listClampsPageSizeAndNegativePage() {
        assertThat(listAndCapturePageable(-3, 500).getPageSize()).isEqualTo(100);
        assertThat(listAndCapturePageable(-3, 0).getPageNumber()).isZero();
        assertThat(listAndCapturePageable(0, 0).getPageSize()).isEqualTo(1);
    }

    @Test
    void listOfAccountNotOwnedDoesNotQueryTransactions() {
        doThrow(new AccountAccessDeniedException(sourceAccountId))
                .when(accountServiceClient).requireOwnedAccount(sourceAccountId);

        assertThatThrownBy(() -> transactionService.listTransactions(sourceAccountId, 0, 20))
                .isInstanceOf(AccountAccessDeniedException.class);
        verify(transactionRepository, never()).findBySourceAccountIdOrDestinationAccountId(any(), any(), any());
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

    private Pageable listAndCapturePageable(int page, int size) {
        when(transactionRepository.findBySourceAccountIdOrDestinationAccountId(eq(sourceAccountId), eq(sourceAccountId), any()))
                .thenAnswer(invocation -> new PageImpl<>(List.of(transaction), invocation.getArgument(2), 1));

        var response = transactionService.listTransactions(sourceAccountId, page, size);
        assertThat(response.transactions()).extracting("id").containsExactly(transaction.getId());

        ArgumentCaptor<Pageable> captor = ArgumentCaptor.forClass(Pageable.class);
        verify(transactionRepository, atLeastOnce())
                .findBySourceAccountIdOrDestinationAccountId(eq(sourceAccountId), eq(sourceAccountId), captor.capture());
        return captor.getValue();
    }

    private TransactionRequest request(BigDecimal amount) {
        return new TransactionRequest(sourceAccountId, lookupId, amount, PaymentType.PIX,
                "device", UUID.randomUUID().toString());
    }
}
