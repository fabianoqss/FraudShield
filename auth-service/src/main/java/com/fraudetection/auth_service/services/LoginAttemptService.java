package com.fraudetection.auth_service.services;

import com.fraudetection.auth_service.validation.Identifiers;
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.ConcurrentMap;

@Service
public class LoginAttemptService {

    private static final int MAX_TRACKED_KEYS = 10_000;

    private final int maxFailures;
    private final Duration lockDuration;
    private final Clock clock;
    // Bounded: once full, the least valuable entries are evicted, so distinct e-mails cannot grow memory
    // without limit. Entries also leave after lockDuration without writes; isExpired still decides the lock.
    private final Cache<String, Attempts> cache;
    private final ConcurrentMap<String, Attempts> attempts;

    @Autowired
    public LoginAttemptService(@Value("${login.max-failures:5}") int maxFailures,
                               @Value("${login.lock-duration:PT15M}") Duration lockDuration) {
        this(maxFailures, lockDuration, Clock.systemUTC(), MAX_TRACKED_KEYS);
    }

    LoginAttemptService(int maxFailures, Duration lockDuration, Clock clock) {
        this(maxFailures, lockDuration, clock, MAX_TRACKED_KEYS);
    }

    LoginAttemptService(int maxFailures, Duration lockDuration, Clock clock, int maxTrackedKeys) {
        this.maxFailures = maxFailures;
        this.lockDuration = lockDuration;
        this.clock = clock;
        this.cache = Caffeine.newBuilder()
                .maximumSize(maxTrackedKeys)
                .expireAfterWrite(lockDuration)
                .executor(Runnable::run)
                .build();
        this.attempts = cache.asMap();
    }

    public boolean isBlocked(String email) {
        Attempts current = attempts.get(key(email));
        return current != null && !isExpired(current) && current.failures() >= maxFailures;
    }

    public void recordFailure(String email) {
        Instant now = clock.instant();
        attempts.compute(key(email), (k, current) -> current == null || isExpired(current)
                ? new Attempts(1, now)
                : new Attempts(current.failures() + 1, current.windowStart()));
    }

    public void recordSuccess(String email) {
        attempts.remove(key(email));
    }

    long trackedKeys() {
        cache.cleanUp();
        return cache.estimatedSize();
    }

    private boolean isExpired(Attempts current) {
        return clock.instant().isAfter(current.windowStart().plus(lockDuration));
    }

    private String key(String email) {
        return email == null ? "" : Identifiers.email(email);
    }

    private record Attempts(int failures, Instant windowStart) {
    }
}
