package com.fraudetection.account_service.pix;

import com.fraudetection.account_service.clients.AuthServiceClient;
import com.fraudetection.account_service.dto.response.UserLookupResponse;
import com.fraudetection.account_service.entities.Account;
import com.fraudetection.account_service.pix.dto.PixKeyLookupResponse;
import com.fraudetection.account_service.pix.exceptions.InvalidPixKeyFormatException;
import com.fraudetection.account_service.pix.exceptions.PixKeyNotFoundException;
import com.fraudetection.account_service.pix.exceptions.PixLookupNotFoundException;
import com.fraudetection.account_service.pix.exceptions.PixLookupRateLimitedException;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class PixLookupServiceTest {

    private static final Instant NOW = Instant.parse("2026-09-24T12:00:00Z");

    private final PixKeyRepository pixKeyRepository = mock(PixKeyRepository.class);
    private final AuthServiceClient authServiceClient = mock(AuthServiceClient.class);
    private final PixLookupRateLimiter rateLimiter = mock(PixLookupRateLimiter.class);
    private final PixLookupStore lookupStore = mock(PixLookupStore.class);
    private final SimpleMeterRegistry meterRegistry = new SimpleMeterRegistry();
    private final PixLookupService service = new PixLookupService(pixKeyRepository, authServiceClient, rateLimiter,
            lookupStore, new PixProperties(Duration.ofMinutes(5), 20), meterRegistry, Clock.fixed(NOW, ZoneOffset.UTC));

    private final UUID requesterId = UUID.randomUUID();
    private final UUID recipientId = UUID.randomUUID();
    private Account recipientAccount;

    @BeforeEach
    void setUp() {
        recipientAccount = new Account();
        recipientAccount.setId(UUID.randomUUID());
        PixKey key = new PixKey();
        key.setKeyType(PixKeyType.EMAIL);
        key.setKeyValue("ana@example.com");
        key.setAccount(recipientAccount);
        key.setOwnerId(recipientId);
        when(pixKeyRepository.findByKeyValue("ana@example.com")).thenReturn(Optional.of(key));
        when(authServiceClient.lookupById(recipientId))
                .thenReturn(new UserLookupResponse(recipientId, "Ana Souza", "ana@example.com", "52998224725"));
    }

    @Test
    void lookupReturnsRecipientWithMaskedCpfAndStoresTheLookup() {
        PixKeyLookupResponse response = service.lookup(requesterId, " Ana@Example.com ");

        assertThat(response.recipientName()).isEqualTo("Ana Souza");
        assertThat(response.maskedCpf()).isEqualTo("***.982.247-**");
        assertThat(response.keyType()).isEqualTo(PixKeyType.EMAIL);
        assertThat(response.expiresAt()).isEqualTo(NOW.plus(Duration.ofMinutes(5)));

        ArgumentCaptor<PixLookup> stored = ArgumentCaptor.forClass(PixLookup.class);
        verify(lookupStore).save(eq(response.lookupId()), stored.capture(), eq(Duration.ofMinutes(5)));
        assertThat(stored.getValue()).isEqualTo(new PixLookup(recipientAccount.getId(), requesterId));
        assertThat(meterRegistry.counter("pix.key.lookups", "result", "found").count()).isEqualTo(1.0);
    }

    @Test
    void lookupOfOwnKeyIsAllowed() {
        when(authServiceClient.lookupById(recipientId))
                .thenReturn(new UserLookupResponse(recipientId, "Ana Souza", "ana@example.com", "52998224725"));

        assertThat(service.lookup(recipientId, "ana@example.com").recipientName()).isEqualTo("Ana Souza");
    }

    @Test
    void unregisteredKeyIsNotFoundAndNothingIsStored() {
        assertThatThrownBy(() -> service.lookup(requesterId, "bia@example.com"))
                .isInstanceOf(PixKeyNotFoundException.class);
        verify(lookupStore, never()).save(any(), any(), any());
        assertThat(meterRegistry.counter("pix.key.lookups", "result", "not_found").count()).isEqualTo(1.0);
    }

    @Test
    void rateLimitIsCheckedBeforeAnyQuery() {
        doThrow(new PixLookupRateLimitedException(10)).when(rateLimiter).acquire(requesterId);

        assertThatThrownBy(() -> service.lookup(requesterId, "ana@example.com"))
                .isInstanceOf(PixLookupRateLimitedException.class);
        verifyNoInteractions(pixKeyRepository);
        assertThat(meterRegistry.counter("pix.key.lookups", "result", "rate_limited").count()).isEqualTo(1.0);
    }

    @Test
    void invalidFormatStillCountsTowardTheRateLimit() {
        assertThatThrownBy(() -> service.lookup(requesterId, "not a key"))
                .isInstanceOf(InvalidPixKeyFormatException.class);
        verify(rateLimiter).acquire(requesterId);
    }

    @Test
    void resolveReturnsTheAccountForTheSameRequester() {
        UUID lookupId = UUID.randomUUID();
        when(lookupStore.find(lookupId)).thenReturn(Optional.of(new PixLookup(recipientAccount.getId(), requesterId)));

        assertThat(service.resolve(lookupId, requesterId)).isEqualTo(recipientAccount.getId());
        assertThat(meterRegistry.counter("pix.lookup.resolutions", "result", "resolved").count()).isEqualTo(1.0);
    }

    @Test
    void resolveByAnotherUserIsNotFound() {
        UUID lookupId = UUID.randomUUID();
        when(lookupStore.find(lookupId)).thenReturn(Optional.of(new PixLookup(recipientAccount.getId(), requesterId)));

        assertThatThrownBy(() -> service.resolve(lookupId, UUID.randomUUID()))
                .isInstanceOf(PixLookupNotFoundException.class);
    }

    @Test
    void resolveOfExpiredLookupIsNotFound() {
        UUID lookupId = UUID.randomUUID();
        when(lookupStore.find(lookupId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.resolve(lookupId, requesterId)).isInstanceOf(PixLookupNotFoundException.class);
        assertThat(meterRegistry.counter("pix.lookup.resolutions", "result", "not_found").count()).isEqualTo(1.0);
    }
}
