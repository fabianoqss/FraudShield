package com.fraudetection.transaction_service.services.exceptions;

public class DuplicateIdempotencyKeyException extends RuntimeException {

    public DuplicateIdempotencyKeyException(String idempotencyKey) {
        super("Transaction already exists for idempotency key: " + idempotencyKey);
    }
}
