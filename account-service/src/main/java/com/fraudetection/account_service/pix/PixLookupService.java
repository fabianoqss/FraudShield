package com.fraudetection.account_service.pix;

import com.fraudetection.account_service.clients.AuthServiceClient;
import com.fraudetection.account_service.dto.response.UserLookupResponse;
import com.fraudetection.account_service.pix.dto.PixKeyLookupResponse;
import com.fraudetection.account_service.pix.exceptions.PixKeyNotFoundException;
import com.fraudetection.account_service.pix.exceptions.PixLookupNotFoundException;
import com.fraudetection.account_service.pix.exceptions.PixLookupRateLimitedException;
import io.micrometer.core.instrument.MeterRegistry;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.util.UUID;

@Slf4j
@Service
public class PixLookupService {

    private final PixKeyRepository pixKeyRepository;
    private final AuthServiceClient authServiceClient;
    private final PixLookupRateLimiter rateLimiter;
    private final PixLookupStore lookupStore;
    private final PixProperties properties;
    private final MeterRegistry meterRegistry;
    private final Clock clock;

    @Autowired
    public PixLookupService(PixKeyRepository pixKeyRepository, AuthServiceClient authServiceClient,
                            PixLookupRateLimiter rateLimiter, PixLookupStore lookupStore, PixProperties properties,
                            MeterRegistry meterRegistry) {
        this(pixKeyRepository, authServiceClient, rateLimiter, lookupStore, properties, meterRegistry,
                Clock.systemUTC());
    }

    PixLookupService(PixKeyRepository pixKeyRepository, AuthServiceClient authServiceClient,
                     PixLookupRateLimiter rateLimiter, PixLookupStore lookupStore, PixProperties properties,
                     MeterRegistry meterRegistry, Clock clock) {
        this.pixKeyRepository = pixKeyRepository;
        this.authServiceClient = authServiceClient;
        this.rateLimiter = rateLimiter;
        this.lookupStore = lookupStore;
        this.properties = properties;
        this.meterRegistry = meterRegistry;
        this.clock = clock;
    }

    public PixKeyLookupResponse lookup(UUID requesterId, String rawKey) {
        try {
            rateLimiter.acquire(requesterId);
        } catch (PixLookupRateLimitedException e) {
            countLookup("rate_limited");
            throw e;
        }

        ParsedPixKey parsed = PixKeyParser.parse(rawKey);
        String masked = PixKeyMasker.mask(parsed.type(), parsed.value());
        PixKey key = pixKeyRepository.findByKeyValue(parsed.value()).orElseThrow(() -> {
            countLookup("not_found");
            log.info("PIX key lookup by user {} for {}: not found", requesterId, masked);
            return new PixKeyNotFoundException();
        });

        UserLookupResponse owner = authServiceClient.lookupById(key.getOwnerId());
        UUID lookupId = UUID.randomUUID();
        lookupStore.save(lookupId, new PixLookup(key.getAccount().getId(), requesterId), properties.lookupTtl());

        countLookup("found");
        log.info("PIX key lookup by user {} for {}: found, lookup {}", requesterId, masked, lookupId);
        return new PixKeyLookupResponse(lookupId, owner.fullName(), PixKeyMasker.maskCpf(owner.cpf()),
                key.getKeyType(), clock.instant().plus(properties.lookupTtl()));
    }

    public UUID resolve(UUID lookupId, UUID requesterId) {
        UUID accountId = lookupStore.find(lookupId)
                .filter(lookup -> lookup.requesterId().equals(requesterId))
                .map(PixLookup::accountId)
                .orElse(null);

        meterRegistry.counter("pix.lookup.resolutions", "result", accountId == null ? "not_found" : "resolved")
                .increment();
        if (accountId == null) {
            throw new PixLookupNotFoundException();
        }
        return accountId;
    }

    private void countLookup(String result) {
        meterRegistry.counter("pix.key.lookups", "result", result).increment();
    }
}
