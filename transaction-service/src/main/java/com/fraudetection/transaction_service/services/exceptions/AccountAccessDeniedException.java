package com.fraudetection.transaction_service.services.exceptions;

import java.util.UUID;

public class AccountAccessDeniedException extends RuntimeException {

    public AccountAccessDeniedException(UUID accountId) {
        super("Account does not belong to the requesting user: " + accountId);
    }
}
