package com.akamai.wsa.enrichment.infrastructure.window;

import com.akamai.wsa.enrichment.domain.port.OffenderWindow;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Component
@ConditionalOnProperty(name = "wsa.storage", havingValue = "inmemory", matchIfMissing = true)
public class InMemoryOffenderWindow implements OffenderWindow {

    private final Map<String, Map<String, Instant>> eventsByClient = new ConcurrentHashMap<>();

    @Override
    public void recordEvent(String clientIp, String eventId, Instant receivedAt) {
        eventsByClient.computeIfAbsent(clientIp, ignored -> new ConcurrentHashMap<>()).put(eventId, receivedAt);
    }

    @Override
    public long countRecentEventsFromClient(String clientIp, Duration window, Instant asOf) {
        Instant windowStart = asOf.minus(window);
        return eventsByClient.getOrDefault(clientIp, Map.of()).values().stream()
                .filter(receivedAt -> !receivedAt.isBefore(windowStart) && !receivedAt.isAfter(asOf))
                .count();
    }
}
