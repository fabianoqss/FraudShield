package com.fraudetection.account_service.pix;

import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataAccessException;
import org.springframework.data.redis.RedisConnectionFailureException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.Optional;
import java.util.UUID;

/** Keeps each confirmed lookup in Redis as "requesterId:accountId" until it expires. Stores IDs only. */
@Component
@RequiredArgsConstructor
public class PixLookupStore {

    private static final String KEY_PREFIX = "pix:lookup:";

    private final StringRedisTemplate redisTemplate;

    public void save(UUID lookupId, PixLookup lookup, Duration ttl) {
        try {
            redisTemplate.opsForValue().set(KEY_PREFIX + lookupId, lookup.requesterId() + ":" + lookup.accountId(), ttl);
        } catch (DataAccessException e) {
            throw new RedisConnectionFailureException("Redis unavailable for PIX key lookups", e);
        }
    }

    public Optional<PixLookup> find(UUID lookupId) {
        String value;
        try {
            value = redisTemplate.opsForValue().get(KEY_PREFIX + lookupId);
        } catch (DataAccessException e) {
            throw new RedisConnectionFailureException("Redis unavailable for PIX key lookups", e);
        }
        if (value == null) {
            return Optional.empty();
        }
        String[] parts = value.split(":", 2);
        return Optional.of(new PixLookup(UUID.fromString(parts[1]), UUID.fromString(parts[0])));
    }
}
