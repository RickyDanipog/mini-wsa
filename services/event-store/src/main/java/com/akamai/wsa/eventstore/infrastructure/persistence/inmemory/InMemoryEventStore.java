package com.akamai.wsa.eventstore.infrastructure.persistence.inmemory;

import com.akamai.wsa.eventstore.domain.model.StoredEvent;
import com.akamai.wsa.eventstore.domain.port.EventStore;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * In-memory system of record for the dry-run pipeline. Idempotent by eventId, so
 * at-least-once Kafka redelivery does not double-count. A Mongo adapter drops in
 * behind this same {@link EventStore} port in the DB-candidate phase.
 */
@Repository
public class InMemoryEventStore implements EventStore {

    private final Map<String, StoredEvent> eventsById = new ConcurrentHashMap<>();

    @Override
    public void saveAll(List<StoredEvent> storedEvents) {
        for (StoredEvent storedEvent : storedEvents) {
            eventsById.putIfAbsent(storedEvent.eventId(), storedEvent);
        }
    }

    @Override
    public long countAll() {
        return eventsById.size();
    }

    @Override
    public List<StoredEvent> findByConfigId(int configId) {
        return eventsById.values().stream()
                .filter(storedEvent -> storedEvent.configId() == configId)
                .toList();
    }
}
