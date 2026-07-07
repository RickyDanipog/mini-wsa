package com.akamai.wsa.enrichment.domain.port;

import java.time.Duration;
import java.time.Instant;

/**
 * Sliding-window record of events per client IP, used to detect repeat offenders.
 * Semantics are record-then-read: the current event is recorded first, then counted.
 */
public interface OffenderWindow {
    void recordEvent(String clientIp, Instant receivedAt);

    long countRecentEventsFromClient(String clientIp, Duration window, Instant asOf);
}
