package com.akamai.wsa.enrichment.domain.port;

public interface ProcessedEventLog {
    boolean markProcessed(String eventId);
}
