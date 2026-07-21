package com.fraudetection.account_service.services;

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

    public boolean alreadyProcessed(UUID eventId) {
        return Boolean.TRUE.equals(redisTemplate.hasKey(PREFIX + eventId));
    }

    public void markProcessed(UUID eventId) {
        redisTemplate.opsForValue().set(PREFIX + eventId, "true", 24, TimeUnit.HOURS);
    }
}
