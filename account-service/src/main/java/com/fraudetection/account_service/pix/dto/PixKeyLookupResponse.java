package com.fraudetection.account_service.pix.dto;

import com.fraudetection.account_service.pix.PixKeyType;

import java.time.Instant;
import java.util.UUID;

public record PixKeyLookupResponse(UUID lookupId, String recipientName, String maskedCpf, PixKeyType keyType,
                                   Instant expiresAt) {
}
