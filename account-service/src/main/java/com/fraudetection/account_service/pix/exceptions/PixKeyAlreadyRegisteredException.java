package com.fraudetection.account_service.pix.exceptions;

public class PixKeyAlreadyRegisteredException extends RuntimeException {

    public PixKeyAlreadyRegisteredException() {
        super("This PIX key is already registered");
    }
}
