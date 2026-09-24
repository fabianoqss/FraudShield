package com.fraudetection.account_service.pix.exceptions;

public class PixKeyNotFoundException extends RuntimeException {

    public PixKeyNotFoundException() {
        super("PIX key not found");
    }
}
