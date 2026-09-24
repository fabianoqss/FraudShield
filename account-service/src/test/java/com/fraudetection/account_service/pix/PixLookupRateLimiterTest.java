package com.fraudetection.account_service.pix;

import com.fraudetection.account_service.pix.exceptions.PixLookupRateLimitedException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.RedisConnectionFailureException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNoException;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.catchThrowableOfType;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class PixLookupRateLimiterTest {

    // 12:00:45 UTC → minute window 29_820_720 of the epoch, 15 s left in it.
    private static final Instant NOW = Instant.parse("2026-09-24T12:00:45Z");

    @SuppressWarnings("unchecked")
    private final ValueOperations<String, String> values = mock(ValueOperations.class);
    private final StringRedisTemplate redis = mock(StringRedisTemplate.class);
    private final PixLookupRateLimiter limiter =
            new PixLookupRateLimiter(redis, 20, Clock.fixed(NOW, ZoneOffset.UTC));
    private final UUID userId = UUID.randomUUID();
    private final String key = "pix:lookup-rate:" + userId + ":" + NOW.getEpochSecond() / 60;

    @BeforeEach
    void setUp() {
        when(redis.opsForValue()).thenReturn(values);
    }

    @Test
    void everyLookupCreatesTheWindowKeyWithItsTtlBeforeCounting() {
        when(values.increment(key)).thenReturn(1L);

        assertThatNoException().isThrownBy(() -> limiter.acquire(userId));

        var inOrderVerifier = inOrder(values, redis);
        inOrderVerifier.verify(values).setIfAbsent(key, "0", Duration.ofMinutes(1));
        inOrderVerifier.verify(values).increment(key);
        verify(redis, never()).expire(anyString(), org.mockito.ArgumentMatchers.any(Duration.class));
    }

    @Test
    void twentiethLookupIsAllowed() {
        when(values.increment(key)).thenReturn(20L);

        assertThatNoException().isThrownBy(() -> limiter.acquire(userId));
    }

    @Test
    void twentyFirstLookupIsRejectedWithSecondsLeftInTheWindow() {
        when(values.increment(key)).thenReturn(21L);

        PixLookupRateLimitedException ex =
                catchThrowableOfType(PixLookupRateLimitedException.class, () -> limiter.acquire(userId));

        assertThat(ex.retryAfterSeconds()).isEqualTo(15);
    }

    @Test
    void nullCounterFailsClosed() {
        when(values.increment(key)).thenReturn(null);

        assertThatThrownBy(() -> limiter.acquire(userId))
                .isInstanceOf(RedisConnectionFailureException.class);
    }
}
