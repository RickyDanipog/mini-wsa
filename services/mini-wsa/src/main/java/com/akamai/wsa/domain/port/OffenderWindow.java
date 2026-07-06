package com.akamai.wsa.domain.port;

import com.akamai.wsa.domain.model.ClientIp;
import com.akamai.wsa.domain.model.TimeWindow;

import java.time.Instant;

/**
 * Outbound port answering the repeat-offender question: how many events from a
 * given client fall inside a look-back window ending at a reference instant.
 *
 * <p>Kept separate from {@link EventStore} so its backing can evolve
 * independently: a store-backed count first, then a Redis sliding window at
 * scale — without touching the scorer or the event store.
 */
public interface OffenderWindow {

    long countRecentEventsFromClient(ClientIp clientIp, TimeWindow timeWindow, Instant asOf);
}
