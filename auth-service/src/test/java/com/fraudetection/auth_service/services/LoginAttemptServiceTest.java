package com.fraudetection.auth_service.services;

import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;

import static org.assertj.core.api.Assertions.assertThat;

class LoginAttemptServiceTest {

    private final MutableClock clock = new MutableClock(Instant.parse("2026-09-22T12:00:00Z"));
    private final LoginAttemptService service = new LoginAttemptService(3, Duration.ofMinutes(15), clock);

    @Test
    void blocksAfterMaxFailuresRegardlessOfEmailCase() {
        service.recordFailure("ana@example.com");
        service.recordFailure("ANA@example.com");
        assertThat(service.isBlocked("ana@example.com")).isFalse();

        service.recordFailure(" ana@example.com ");

        assertThat(service.isBlocked("Ana@Example.com")).isTrue();
        assertThat(service.isBlocked("bob@example.com")).isFalse();
    }

    @Test
    void unblocksAfterLockDuration() {
        for (int i = 0; i < 3; i++) {
            service.recordFailure("ana@example.com");
        }

        clock.advance(Duration.ofMinutes(16));

        assertThat(service.isBlocked("ana@example.com")).isFalse();
    }

    @Test
    void successfulLoginResetsTheCounter() {
        service.recordFailure("ana@example.com");
        service.recordFailure("ana@example.com");
        service.recordSuccess("ana@example.com");
        service.recordFailure("ana@example.com");
        service.recordFailure("ana@example.com");

        assertThat(service.isBlocked("ana@example.com")).isFalse();
    }

    private static final class MutableClock extends Clock {
        private Instant now;

        MutableClock(Instant now) {
            this.now = now;
        }

        void advance(Duration duration) {
            now = now.plus(duration);
        }

        @Override
        public ZoneId getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return now;
        }
    }
}
