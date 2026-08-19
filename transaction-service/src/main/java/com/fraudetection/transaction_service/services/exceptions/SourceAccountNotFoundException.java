package com.fraudetection.transaction_service.services.exceptions;

import java.util.UUID;

public class SourceAccountNotFoundException extends RuntimeException {

    public SourceAccountNotFoundException(UUID accountId) {
        super("Source account not found: " + accountId);
    }
}
