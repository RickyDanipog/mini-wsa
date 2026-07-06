package com.akamai.wsa.infrastructure.persistence.inmemory;

import com.akamai.wsa.domain.model.SecurityEvent;
import com.akamai.wsa.domain.port.EventStore;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * In-memory reference adapter for {@link EventStore} — the "cached DB" used for
 * dry runs and fast tests. Replaced by MongoDB behind the same port later.
 */
@Repository
public class InMemoryEventStore implements EventStore {

    private final List<SecurityEvent> events = new CopyOnWriteArrayList<>();

    @Override
    public void saveAll(List<SecurityEvent> securityEvents) {
        events.addAll(securityEvents);
    }

    @Override
    public long countAll() {
        return events.size();
    }

    @Override
    public List<SecurityEvent> findByConfigId(int configId) {
        return events.stream()
                .filter(securityEvent -> securityEvent.configId() == configId)
                .toList();
    }
}
