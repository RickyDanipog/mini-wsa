package com.akamai.wsa.infrastructure.persistence.inmemory;

import com.akamai.wsa.domain.model.SecurityEvent;
import com.akamai.wsa.domain.port.EventRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

@Repository
public class InMemoryEventRepository implements EventRepository {

    private final List<SecurityEvent> events = new CopyOnWriteArrayList<>();

    @Override
    public void save(SecurityEvent event) {
        events.add(event);
    }

    @Override
    public long count() {
        return events.size();
    }

    @Override
    public List<SecurityEvent> findByConfigId(int configId) {
        return events.stream()
                .filter(event -> event.configId() == configId)
                .toList();
    }
}
