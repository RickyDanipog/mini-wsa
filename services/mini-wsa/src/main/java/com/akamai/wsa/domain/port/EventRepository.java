package com.akamai.wsa.domain.port;

import com.akamai.wsa.domain.model.SecurityEvent;

import java.util.List;

// Domain-owned outbound port. Any storage engine (in-memory, Postgres, Mongo,
// ...) is an adapter implementing this interface. The domain never depends on
// a concrete store.
public interface EventRepository {

    void save(SecurityEvent event);

    long count();

    List<SecurityEvent> findByConfigId(int configId);
}
