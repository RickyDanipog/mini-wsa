package com.akamai.wsa.enrichment.domain.port;

/** Idempotency ledger keyed by eventId, so at-least-once redelivery is processed once. */
public interface ProcessedEventLog {
    /** @return true if this eventId was newly recorded; false if it was already seen. */
    boolean markProcessed(String eventId);
}
