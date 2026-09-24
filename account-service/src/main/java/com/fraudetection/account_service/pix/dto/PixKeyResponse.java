package com.fraudetection.account_service.pix.dto;

import com.fraudetection.account_service.pix.PixKey;
import com.fraudetection.account_service.pix.PixKeyType;

import java.time.LocalDateTime;
import java.util.UUID;

public record PixKeyResponse(UUID id, PixKeyType type, String value, UUID accountId, LocalDateTime createdAt) {

    public static PixKeyResponse from(PixKey key) {
        return new PixKeyResponse(key.getId(), key.getKeyType(), key.getKeyValue(), key.getAccount().getId(),
                key.getCreatedAt());
    }
}
