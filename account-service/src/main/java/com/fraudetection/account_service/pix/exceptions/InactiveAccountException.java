package com.fraudetection.account_service.pix.exceptions;

import java.util.UUID;

public class InactiveAccountException extends RuntimeException {

    public InactiveAccountException(UUID accountId) {
        super("Account is not active: " + accountId);
    }
}
