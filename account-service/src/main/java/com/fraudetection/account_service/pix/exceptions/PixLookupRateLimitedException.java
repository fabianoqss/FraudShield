package com.fraudetection.account_service.pix.exceptions;

public class PixLookupRateLimitedException extends RuntimeException {

    private final long retryAfterSeconds;

    public PixLookupRateLimitedException(long retryAfterSeconds) {
        super("Too many PIX key lookups, try again later");
        this.retryAfterSeconds = retryAfterSeconds;
    }

    public long retryAfterSeconds() {
        return retryAfterSeconds;
    }
}
