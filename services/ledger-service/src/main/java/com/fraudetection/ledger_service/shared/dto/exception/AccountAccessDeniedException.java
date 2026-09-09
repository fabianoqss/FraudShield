package com.fraudetection.ledger_service.shared.dto.exception;

import java.util.UUID;

public class AccountAccessDeniedException extends RuntimeException {

    public AccountAccessDeniedException(UUID accountId) {
        super("Access denied for account: " + accountId);
    }
}
