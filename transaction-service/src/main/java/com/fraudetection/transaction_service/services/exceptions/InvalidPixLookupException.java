package com.fraudetection.transaction_service.services.exceptions;

public class InvalidPixLookupException extends RuntimeException {

    public InvalidPixLookupException() {
        super("PIX key lookup expired or invalid, look up the key again");
    }
}
