package com.fraudetection.ledger_service.infrastructure.redis;

import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.util.UUID;
import java.util.concurrent.TimeUnit;

@Service
@RequiredArgsConstructor
public class IdempotencyService {

    private static final String PREFIX = "processed:";

    private final StringRedisTemplate redisTemplate;

    public boolean isProcessed(UUID eventId) {
        String idempotencyKey = PREFIX + eventId;
        return Boolean.TRUE.equals(redisTemplate.hasKey(idempotencyKey));
    }

    public void markProcessed(UUID eventId) {
        String idempotencyKey = PREFIX + eventId;
        redisTemplate.opsForValue().set(idempotencyKey, "true", 24, TimeUnit.HOURS);
    }
}
