package com.fraudetection.transaction_service.services.exceptions;

import java.util.UUID;

public class InsufficientFundsException extends RuntimeException {

    public InsufficientFundsException(UUID accountId) {
        super("Insufficient available balance on account: " + accountId);
    }
}
