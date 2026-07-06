package com.akamai.wsa.domain.port;

import com.akamai.wsa.domain.model.SecurityEvent;

import java.util.List;

/**
 * Outbound port for durable storage and retrieval of enriched events.
 * Implemented by an adapter per engine (in-memory now; MongoDB and PostgreSQL later).
 *
 * <p>Analytical query methods (statistics aggregation, sample retrieval) are
 * added to this port as the Stats and Samples parts are implemented — each part's
 * plan extends this interface and every adapter alongside it.
 */
public interface EventStore {

    void saveAll(List<SecurityEvent> securityEvents);

    long countAll();

    List<SecurityEvent> findByConfigId(int configId);
}
