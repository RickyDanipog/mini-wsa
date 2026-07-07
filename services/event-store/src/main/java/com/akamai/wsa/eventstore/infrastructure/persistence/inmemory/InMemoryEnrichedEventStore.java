package com.akamai.wsa.eventstore.infrastructure.persistence.inmemory;

import com.akamai.wsa.contracts.EnrichedEventMessage;
import org.springframework.stereotype.Repository;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * In-memory system of record for the dry-run pipeline. Idempotent by eventId, so
 * at-least-once Kafka redelivery does not double-count. Replaced by a Mongo
 * adapter behind this same responsibility in the DB-candidate phase.
 */
@Repository
public class InMemoryEnrichedEventStore {

    private final Map<String, EnrichedEventMessage> eventsByEventId = new ConcurrentHashMap<>();

    public void save(EnrichedEventMessage enrichedEvent) {
        eventsByEventId.putIfAbsent(enrichedEvent.rawEvent().eventId(), enrichedEvent);
    }

    public long count() {
        return eventsByEventId.size();
    }

    public boolean containsEventId(String eventId) {
        return eventsByEventId.containsKey(eventId);
    }
}
