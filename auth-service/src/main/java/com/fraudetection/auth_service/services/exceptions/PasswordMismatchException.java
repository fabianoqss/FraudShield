package com.fraudetection.auth_service.services.exceptions;

public class PasswordMismatchException extends RuntimeException {

    public PasswordMismatchException() {
        super("New password and confirmation do not match");
    }
}
