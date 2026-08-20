package com.fraudetection.auth_service.services.exceptions;

public class UserNotFoundException extends RuntimeException {

    public UserNotFoundException() {
        super("No user found for the given PIX key");
    }
}
