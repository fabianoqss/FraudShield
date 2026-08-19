package com.fraudetection.transaction_service.services.exceptions;

import java.util.UUID;

public class SourceAccountAccessDeniedException extends RuntimeException {

    public SourceAccountAccessDeniedException(UUID accountId) {
        super("Source account does not belong to the requesting user: " + accountId);
    }
}
