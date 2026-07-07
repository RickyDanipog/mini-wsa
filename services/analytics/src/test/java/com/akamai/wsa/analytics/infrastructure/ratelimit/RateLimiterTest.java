package com.akamai.wsa.analytics.infrastructure.ratelimit;

import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;

import static org.assertj.core.api.Assertions.assertThat;

class RateLimiterTest {

    private static final Instant START = Instant.parse("2026-07-06T00:00:00Z");

    @Test
    void allowsUpToMaxRequestsThenDeniesWithinWindow() {
        RateLimiter rateLimiter = new RateLimiter(3, Duration.ofSeconds(60), Clock.fixed(START, ZoneOffset.UTC));

        assertThat(rateLimiter.tryAcquire("client-a")).isTrue();
        assertThat(rateLimiter.tryAcquire("client-a")).isTrue();
        assertThat(rateLimiter.tryAcquire("client-a")).isTrue();
        assertThat(rateLimiter.tryAcquire("client-a")).isFalse();
        assertThat(rateLimiter.tryAcquire("client-a")).isFalse();
    }

    @Test
    void resetsWhenClockAdvancesPastWindow() {
        MutableClock clock = new MutableClock(START);
        RateLimiter rateLimiter = new RateLimiter(2, Duration.ofSeconds(60), clock);

        assertThat(rateLimiter.tryAcquire("client-a")).isTrue();
        assertThat(rateLimiter.tryAcquire("client-a")).isTrue();
        assertThat(rateLimiter.tryAcquire("client-a")).isFalse();

        clock.advance(Duration.ofSeconds(60));

        assertThat(rateLimiter.tryAcquire("client-a")).isTrue();
        assertThat(rateLimiter.tryAcquire("client-a")).isTrue();
        assertThat(rateLimiter.tryAcquire("client-a")).isFalse();
    }

    @Test
    void tracksEachClientKeyIndependently() {
        RateLimiter rateLimiter = new RateLimiter(1, Duration.ofSeconds(60), Clock.fixed(START, ZoneOffset.UTC));

        assertThat(rateLimiter.tryAcquire("client-a")).isTrue();
        assertThat(rateLimiter.tryAcquire("client-a")).isFalse();
        assertThat(rateLimiter.tryAcquire("client-b")).isTrue();
        assertThat(rateLimiter.tryAcquire("client-b")).isFalse();
    }

    private static final class MutableClock extends Clock {

        private Instant current;

        private MutableClock(Instant current) {
            this.current = current;
        }

        private void advance(Duration duration) {
            this.current = this.current.plus(duration);
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
            return current;
        }
    }
}
