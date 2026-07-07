package com.akamai.wsa.enrichment.domain.port;

import java.time.Duration;
import java.time.Instant;

public interface OffenderWindow {
    void recordEvent(String clientIp, Instant receivedAt);

    long countRecentEventsFromClient(String clientIp, Duration window, Instant asOf);
}
