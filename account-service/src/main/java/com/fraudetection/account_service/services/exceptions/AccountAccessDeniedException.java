package com.fraudetection.account_service.services.exceptions;

public class AccountAccessDeniedException extends RuntimeException {

    public AccountAccessDeniedException(String message) {
        super(message);
    }
}
