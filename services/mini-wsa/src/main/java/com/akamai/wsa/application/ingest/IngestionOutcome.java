package com.akamai.wsa.application.ingest;

import com.akamai.wsa.domain.model.SecurityEvent;

import java.util.List;

/**
 * Result of a successful ingestion (all-or-nothing): how many events were
 * accepted and the enriched events that were stored.
 */
public record IngestionOutcome(int acceptedCount, List<SecurityEvent> storedEvents) {
}
