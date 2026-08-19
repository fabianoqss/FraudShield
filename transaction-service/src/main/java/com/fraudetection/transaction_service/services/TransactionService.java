package com.fraudetection.transaction_service.services;

import com.fraudetection.transaction_service.clients.AccountServiceClient;
import com.fraudetection.transaction_service.dto.request.TransactionRequest;
import com.fraudetection.transaction_service.dto.response.TransactionResponse;
import com.fraudetection.transaction_service.entities.Transaction;
import com.fraudetection.transaction_service.enums.PaymentStatus;
import com.fraudetection.transaction_service.kafka.producers.TransactionCreatedProducer;
import com.fraudetection.transaction_service.repositories.TransactionRepository;
import com.fraudetection.transaction_service.services.exceptions.DuplicateIdempotencyKeyException;
import com.fraudetection.transaction_service.services.exceptions.TransactionNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class TransactionService {

    private final TransactionRepository transactionRepository;
    private final TransactionCreatedProducer transactionCreatedProducer;
    private final AccountServiceClient accountServiceClient;

    @Transactional
    public TransactionResponse createTransaction(TransactionRequest request, UUID requestingUserId) {
        if (transactionRepository.existsByIdempotencyKey(request.idempotencyKey())) {
            throw new DuplicateIdempotencyKeyException(request.idempotencyKey());
        }

        accountServiceClient.verifySourceAccountOwnership(request.sourceAccountId(), requestingUserId);

        Transaction transaction = new Transaction();
        transaction.setSourceAccountId(request.sourceAccountId());
        transaction.setDestinationAccountId(request.destinationAccountId());
        transaction.setAmount(request.amount());
        transaction.setType(request.type());
        transaction.setStatus(PaymentStatus.CREATED);
        transaction.setIdempotencyKey(request.idempotencyKey());
        transaction.setDeviceId(request.deviceId());
        transaction.setIpAddress(request.ipAddress());
        transaction.setCreatedAt(LocalDateTime.now());

        transactionRepository.save(transaction);
        transactionCreatedProducer.publish(transaction);

        return toResponse(transaction);
    }

    public TransactionResponse getTransaction(UUID id) {
        Transaction transaction = transactionRepository.findById(id)
                .orElseThrow(() -> new TransactionNotFoundException(id));

        return toResponse(transaction);
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
