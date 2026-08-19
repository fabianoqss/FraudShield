package com.fraudetection.transaction_service.services.exceptions;

public class AccountServiceUnavailableException extends RuntimeException {

    public AccountServiceUnavailableException() {
        super("Could not verify source account ownership: account-service is unavailable");
    }
}
