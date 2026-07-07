package com.akamai.wsa.eventstore.infrastructure.persistence.inmemory;

import com.akamai.wsa.eventstore.domain.model.StoredEvent;
import com.akamai.wsa.eventstore.domain.port.EventStore;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Repository
@ConditionalOnProperty(name = "wsa.storage", havingValue = "inmemory", matchIfMissing = true)
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
