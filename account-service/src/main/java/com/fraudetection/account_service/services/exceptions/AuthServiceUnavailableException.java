package com.fraudetection.account_service.services.exceptions;

public class AuthServiceUnavailableException extends RuntimeException {

    public AuthServiceUnavailableException() {
        super("Could not resolve PIX key: auth-service is unavailable");
    }
}
