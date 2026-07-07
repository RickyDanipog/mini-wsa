package com.akamai.wsa.eventstore.domain.port;

import com.akamai.wsa.eventstore.domain.model.StoredEvent;

import java.util.List;

/** Outbound port for the enriched-event system of record. Saves are idempotent on eventId. */
public interface EventStore {
    void saveAll(List<StoredEvent> storedEvents);

    long countAll();

    List<StoredEvent> findByConfigId(int configId);
}
