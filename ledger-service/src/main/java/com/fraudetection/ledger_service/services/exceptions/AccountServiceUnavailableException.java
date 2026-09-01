package com.fraudetection.ledger_service.services.exceptions;

public class AccountServiceUnavailableException extends RuntimeException {

    public AccountServiceUnavailableException() {
        super("Could not verify account ownership: account-service is unavailable");
    }
}
