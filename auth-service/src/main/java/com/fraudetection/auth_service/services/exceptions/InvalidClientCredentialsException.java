package com.fraudetection.auth_service.services.exceptions;

public class InvalidClientCredentialsException extends RuntimeException {

    public InvalidClientCredentialsException() {
        super("Invalid client credentials");
    }
}
