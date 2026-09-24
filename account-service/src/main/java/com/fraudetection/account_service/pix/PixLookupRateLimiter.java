package com.fraudetection.account_service.pix;

import com.fraudetection.account_service.pix.exceptions.PixLookupRateLimitedException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.RedisConnectionFailureException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.Duration;
import java.util.UUID;

/**
 * Fixed one-minute window per user in Redis. Every lookup counts, including not-found and malformed ones,
 * so probing keys (e.g. guessing CPFs) is bounded.
 */
@Component
public class PixLookupRateLimiter {

    private static final Duration WINDOW = Duration.ofMinutes(1);
    private static final String KEY_PREFIX = "pix:lookup-rate:";

    private final StringRedisTemplate redisTemplate;
    private final int maxPerWindow;
    private final Clock clock;

    @Autowired
    public PixLookupRateLimiter(StringRedisTemplate redisTemplate, PixProperties properties) {
        this(redisTemplate, properties.lookupsPerMinute(), Clock.systemUTC());
    }

    PixLookupRateLimiter(StringRedisTemplate redisTemplate, int maxPerWindow, Clock clock) {
        this.redisTemplate = redisTemplate;
        this.maxPerWindow = maxPerWindow;
        this.clock = clock;
    }

    public void acquire(UUID userId) {
        long epochSecond = clock.instant().getEpochSecond();
        long windowSeconds = WINDOW.toSeconds();
        String key = KEY_PREFIX + userId + ":" + epochSecond / windowSeconds;

        redisTemplate.opsForValue().setIfAbsent(key, "0", WINDOW);
        Long count = redisTemplate.opsForValue().increment(key);
        if (count == null) {
            throw new RedisConnectionFailureException("Redis returned no lookup counter");
        }
        if (count > maxPerWindow) {
            throw new PixLookupRateLimitedException(windowSeconds - epochSecond % windowSeconds);
        }
    }
}
