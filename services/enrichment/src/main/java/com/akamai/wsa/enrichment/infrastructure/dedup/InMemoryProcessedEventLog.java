package com.akamai.wsa.enrichment.infrastructure.dedup;

import com.akamai.wsa.enrichment.domain.port.ProcessedEventLog;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentHashMap.KeySetView;

@Component
@ConditionalOnProperty(name = "wsa.storage", havingValue = "inmemory", matchIfMissing = true)
public class InMemoryProcessedEventLog implements ProcessedEventLog {

    private final KeySetView<String, Boolean> seenEventIds = ConcurrentHashMap.newKeySet();

    @Override
    public boolean markProcessed(String eventId) {
        return seenEventIds.add(eventId);
    }
}
