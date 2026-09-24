package com.fraudetection.transaction_service.services;

import com.fraudetection.transaction_service.clients.AccountServiceClient;
import com.fraudetection.transaction_service.dto.request.TransactionRequest;
import com.fraudetection.transaction_service.dto.response.TransactionPageResponse;
import com.fraudetection.transaction_service.dto.response.TransactionResponse;
import com.fraudetection.transaction_service.entities.Transaction;
import com.fraudetection.transaction_service.enums.PaymentStatus;
import com.fraudetection.transaction_service.kafka.producers.TransactionCreatedProducer;
import com.fraudetection.transaction_service.repositories.TransactionRepository;
import com.fraudetection.transaction_service.services.exceptions.DuplicateIdempotencyKeyException;
import com.fraudetection.transaction_service.services.exceptions.InsufficientFundsException;
import com.fraudetection.transaction_service.services.exceptions.SameAccountTransferException;
import com.fraudetection.transaction_service.services.exceptions.TransactionNotFoundException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class TransactionService {

    private static final int MAX_PAGE_SIZE = 100;

    private final TransactionRepository transactionRepository;
    private final TransactionCreatedProducer transactionCreatedProducer;
    private final AccountServiceClient accountServiceClient;

    // Not @Transactional on purpose: the account-service calls must not hold a DB connection, and
    // transaction.created must only be published once the row is committed. save() commits on return.
    public TransactionResponse createTransaction(TransactionRequest request) {
        if (transactionRepository.existsByIdempotencyKey(request.idempotencyKey())) {
            throw new DuplicateIdempotencyKeyException(request.idempotencyKey());
        }

        BigDecimal availableBalance = accountServiceClient.getOwnedAvailableBalance(request.sourceAccountId());
        if (availableBalance.compareTo(request.amount()) < 0) {
            throw new InsufficientFundsException(request.sourceAccountId());
        }

        UUID destinationAccountId = accountServiceClient.resolvePixLookup(request.lookupId());
        if (destinationAccountId.equals(request.sourceAccountId())) {
            throw new SameAccountTransferException();
        }

        Transaction transaction = new Transaction();
        transaction.setSourceAccountId(request.sourceAccountId());
        transaction.setDestinationAccountId(destinationAccountId);
        transaction.setAmount(request.amount());
        transaction.setType(request.type());
        transaction.setStatus(PaymentStatus.CREATED);
        transaction.setIdempotencyKey(request.idempotencyKey());
        transaction.setDeviceId(request.deviceId());
        transaction.setIpAddress(request.ipAddress());
        transaction.setCreatedAt(LocalDateTime.now());

        Transaction saved = transactionRepository.save(transaction);
        transactionCreatedProducer.publish(saved);

        return toResponse(saved);
    }

    public TransactionResponse getTransaction(UUID id) {
        Transaction transaction = transactionRepository.findById(id)
                .orElseThrow(() -> new TransactionNotFoundException(id));

        if (!accountServiceClient.ownsAccount(transaction.getSourceAccountId())
                && !accountServiceClient.ownsAccount(transaction.getDestinationAccountId())) {
            throw new TransactionNotFoundException(id);
        }

        return toResponse(transaction);
    }

    public TransactionPageResponse listTransactions(UUID accountId, int page, int size) {
        accountServiceClient.requireOwnedAccount(accountId);

        PageRequest pageRequest = PageRequest.of(
                Math.max(page, 0),
                Math.clamp(size, 1, MAX_PAGE_SIZE),
                Sort.by(Sort.Direction.DESC, "createdAt"));
        Page<Transaction> result = transactionRepository
                .findBySourceAccountIdOrDestinationAccountId(accountId, accountId, pageRequest);

        return new TransactionPageResponse(
                result.getContent().stream().map(this::toResponse).toList(),
                result.getNumber(),
                result.getSize(),
                result.getTotalElements(),
                result.getTotalPages()
        );
    }

    @Transactional
    public void applyOutcome(UUID transactionId, PaymentStatus outcome) {
        int updated = transactionRepository.updateStatusIf(transactionId, PaymentStatus.CREATED, outcome);
        if (updated == 1) {
            log.info("Transaction {} is now {}", transactionId, outcome);
        } else if (transactionRepository.existsById(transactionId)) {
            log.info("Transaction {} already has its outcome, ignoring {}", transactionId, outcome);
        } else {
            log.warn("Outcome {} received for unknown transaction {}", outcome, transactionId);
        }
    }

    private TransactionResponse toResponse(Transaction transaction) {
        return new TransactionResponse(
                transaction.getId(),
                transaction.getSourceAccountId(),
                transaction.getDestinationAccountId(),
                transaction.getAmount(),
                transaction.getType(),
                transaction.getStatus(),
                transaction.getCreatedAt()
        );
    }
}
