package com.akamai.wsa.enrichment.infrastructure.dedup;

import com.akamai.wsa.enrichment.domain.port.ProcessedEventLog;
import org.springframework.stereotype.Component;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentHashMap.KeySetView;

/** In-memory dedup ledger (Redis/DB deferred). Per-instance only — fine for dry runs. */
@Component
public class InMemoryProcessedEventLog implements ProcessedEventLog {

    private final KeySetView<String, Boolean> seenEventIds = ConcurrentHashMap.newKeySet();

    @Override
    public boolean markProcessed(String eventId) {
        return seenEventIds.add(eventId);
    }
}
