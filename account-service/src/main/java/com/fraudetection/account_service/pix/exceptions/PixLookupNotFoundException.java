package com.fraudetection.account_service.pix.exceptions;

public class PixLookupNotFoundException extends RuntimeException {

    public PixLookupNotFoundException() {
        super("PIX key lookup not found or expired");
    }
}
