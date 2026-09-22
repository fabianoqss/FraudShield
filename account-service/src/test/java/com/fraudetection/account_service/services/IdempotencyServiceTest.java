package com.fraudetection.account_service.services;

import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.util.UUID;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class IdempotencyServiceTest {

    private final StringRedisTemplate redisTemplate = mock(StringRedisTemplate.class);

    @Test
    @SuppressWarnings("unchecked")
    void keysAreNamespacedPerServiceSoConsumersDoNotShadowEachOther() {
        ValueOperations<String, String> values = mock(ValueOperations.class);
        when(redisTemplate.opsForValue()).thenReturn(values);
        UUID eventId = UUID.randomUUID();
        IdempotencyService service = new IdempotencyService(redisTemplate, "account-service");

        service.markProcessed(eventId);

        verify(values).set("processed:account-service:" + eventId, "true", 24, TimeUnit.HOURS);
    }

    @Test
    void eventMarkedByAnotherServiceIsNotConsideredProcessed() {
        UUID eventId = UUID.randomUUID();
        when(redisTemplate.hasKey("processed:other-service:" + eventId)).thenReturn(true);
        when(redisTemplate.hasKey("processed:account-service:" + eventId)).thenReturn(false);

        assertThat(new IdempotencyService(redisTemplate, "account-service").alreadyProcessed(eventId)).isFalse();
    }
}
