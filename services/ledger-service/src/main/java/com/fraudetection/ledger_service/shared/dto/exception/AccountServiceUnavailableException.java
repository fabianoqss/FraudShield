package com.fraudetection.ledger_service.shared.dto.exception;

public class AccountServiceUnavailableException extends RuntimeException {

    public AccountServiceUnavailableException() {
        super("Account service is currently unavailable");
    }
}
