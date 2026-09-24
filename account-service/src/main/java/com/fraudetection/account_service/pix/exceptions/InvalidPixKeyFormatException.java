package com.fraudetection.account_service.pix.exceptions;

public class InvalidPixKeyFormatException extends RuntimeException {

    public InvalidPixKeyFormatException() {
        super("Invalid PIX key format");
    }
}
