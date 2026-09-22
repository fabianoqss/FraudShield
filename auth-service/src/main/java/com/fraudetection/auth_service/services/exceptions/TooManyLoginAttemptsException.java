package com.fraudetection.auth_service.services.exceptions;

public class TooManyLoginAttemptsException extends RuntimeException {

    public TooManyLoginAttemptsException() {
        super("Too many failed login attempts, try again later");
    }
}
