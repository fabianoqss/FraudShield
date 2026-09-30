package com.fraudetection.account_service.pix.exceptions;

public class InvalidPixKeyFormatException extends RuntimeException {

    public InvalidPixKeyFormatException() {
        super("Invalid PIX key format. Use a CPF (11 digits), an e-mail or a random key (UUID).");
    }
}
