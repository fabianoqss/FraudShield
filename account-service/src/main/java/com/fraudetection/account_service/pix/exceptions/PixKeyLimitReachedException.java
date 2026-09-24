package com.fraudetection.account_service.pix.exceptions;

public class PixKeyLimitReachedException extends RuntimeException {

    public PixKeyLimitReachedException() {
        super("An account can have at most 5 PIX keys");
    }
}
