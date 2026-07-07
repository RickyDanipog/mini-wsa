package com.akamai.wsa.enrichment.infrastructure.window;

import com.akamai.wsa.enrichment.domain.port.OffenderWindow;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

@Component
@ConditionalOnProperty(name = "wsa.storage", havingValue = "inmemory", matchIfMissing = true)
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
