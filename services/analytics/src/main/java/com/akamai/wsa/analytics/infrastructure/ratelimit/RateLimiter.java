package com.akamai.wsa.analytics.infrastructure.ratelimit;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.ConcurrentHashMap;

public class RateLimiter {

    private final int maxRequests;
    private final Duration windowDuration;
    private final Clock clock;
    private final ConcurrentHashMap<String, Window> windowsByClientKey = new ConcurrentHashMap<>();

    public RateLimiter(int maxRequests, Duration windowDuration, Clock clock) {
        this.maxRequests = maxRequests;
        this.windowDuration = windowDuration;
        this.clock = clock;
    }

    public boolean tryAcquire(String clientKey) {
        Instant now = clock.instant();
        Window window = windowsByClientKey.compute(clientKey, (key, existing) -> {
            if (existing == null || !now.isBefore(existing.windowStart().plus(windowDuration))) {
                return new Window(now, 1);
            }
            return new Window(existing.windowStart(), existing.count() + 1);
        });
        return window.count() <= maxRequests;
    }

    private record Window(Instant windowStart, int count) {
    }
}
