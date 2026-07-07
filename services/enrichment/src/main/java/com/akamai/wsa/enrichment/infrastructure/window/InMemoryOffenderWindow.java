package com.akamai.wsa.enrichment.infrastructure.window;

import com.akamai.wsa.enrichment.domain.port.OffenderWindow;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * In-memory repeat-offender window (Redis deferred). Per-instance only — correct for
 * single-instance dry runs; horizontal scaling needs the Redis window and
 * {@code events.raw} partitioned by clientIp so one IP maps to one consumer.
 */
@Component
public class InMemoryOffenderWindow implements OffenderWindow {

    private final Map<String, List<Instant>> eventsByClient = new ConcurrentHashMap<>();

    @Override
    public void recordEvent(String clientIp, Instant receivedAt) {
        eventsByClient.computeIfAbsent(clientIp, ignored -> new CopyOnWriteArrayList<>()).add(receivedAt);
    }

    @Override
    public long countRecentEventsFromClient(String clientIp, Duration window, Instant asOf) {
        Instant windowStart = asOf.minus(window);
        return eventsByClient.getOrDefault(clientIp, List.of()).stream()
                .filter(receivedAt -> !receivedAt.isBefore(windowStart) && !receivedAt.isAfter(asOf))
                .count();
    }
}
