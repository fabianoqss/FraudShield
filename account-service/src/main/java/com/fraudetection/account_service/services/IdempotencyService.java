package com.fraudetection.account_service.services;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.util.UUID;
import java.util.concurrent.TimeUnit;

@Service
public class IdempotencyService {

    private final StringRedisTemplate redisTemplate;
    private final String keyPrefix;

    public IdempotencyService(StringRedisTemplate redisTemplate,
                              @Value("${spring.application.name}") String applicationName) {
        this.redisTemplate = redisTemplate;
        this.keyPrefix = "processed:" + applicationName + ":";
    }

    public boolean alreadyProcessed(UUID eventId) {
        return Boolean.TRUE.equals(redisTemplate.hasKey(keyPrefix + eventId));
    }

    public void markProcessed(UUID eventId) {
        redisTemplate.opsForValue().set(keyPrefix + eventId, "true", 24, TimeUnit.HOURS);
    }
}
