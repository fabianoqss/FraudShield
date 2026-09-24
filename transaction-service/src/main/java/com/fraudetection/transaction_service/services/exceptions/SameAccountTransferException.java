package com.fraudetection.transaction_service.services.exceptions;

public class SameAccountTransferException extends RuntimeException {

    public SameAccountTransferException() {
        super("Cannot transfer to the same account");
    }
}
