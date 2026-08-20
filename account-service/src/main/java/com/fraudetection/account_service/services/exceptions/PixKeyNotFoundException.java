package com.fraudetection.account_service.services.exceptions;

public class PixKeyNotFoundException extends RuntimeException {

    public PixKeyNotFoundException() {
        super("No account found for the given PIX key");
    }
}
