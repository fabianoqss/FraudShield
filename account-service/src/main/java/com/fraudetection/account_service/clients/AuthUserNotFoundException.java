package com.fraudetection.account_service.clients;

import java.util.UUID;

public class AuthUserNotFoundException extends IllegalStateException {
    public AuthUserNotFoundException(UUID userId) {
        super("User " + userId + " not found in auth-service");
    }
}
