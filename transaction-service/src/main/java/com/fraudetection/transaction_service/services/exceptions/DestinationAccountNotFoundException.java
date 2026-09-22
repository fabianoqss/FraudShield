package com.fraudetection.transaction_service.services.exceptions;

import java.util.UUID;

public class DestinationAccountNotFoundException extends RuntimeException {

    public DestinationAccountNotFoundException(UUID accountId) {
        super("Destination account not found: " + accountId);
    }
}
