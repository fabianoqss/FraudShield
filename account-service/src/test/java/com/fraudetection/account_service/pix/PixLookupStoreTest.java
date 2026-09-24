package com.fraudetection.account_service.pix;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.time.Duration;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class PixLookupStoreTest {

    @SuppressWarnings("unchecked")
    private final ValueOperations<String, String> values = mock(ValueOperations.class);
    private final StringRedisTemplate redis = mock(StringRedisTemplate.class);
    private final PixLookupStore store = new PixLookupStore(redis);

    @BeforeEach
    void setUp() {
        when(redis.opsForValue()).thenReturn(values);
    }

    @Test
    void savesOnlyIdsWithTheTtl() {
        UUID lookupId = UUID.randomUUID();
        PixLookup lookup = new PixLookup(UUID.randomUUID(), UUID.randomUUID());

        store.save(lookupId, lookup, Duration.ofMinutes(5));

        verify(values).set("pix:lookup:" + lookupId, lookup.requesterId() + ":" + lookup.accountId(), Duration.ofMinutes(5));
    }

    @Test
    void findsASavedLookup() {
        UUID lookupId = UUID.randomUUID();
        PixLookup lookup = new PixLookup(UUID.randomUUID(), UUID.randomUUID());
        when(values.get("pix:lookup:" + lookupId)).thenReturn(lookup.requesterId() + ":" + lookup.accountId());

        assertThat(store.find(lookupId)).contains(lookup);
    }

    @Test
    void missingOrExpiredLookupIsEmpty() {
        assertThat(store.find(UUID.randomUUID())).isEmpty();
    }
}
