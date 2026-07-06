package com.akamai.wsa.domain.model;

import java.time.Instant;

/**
 * Aggregate root: an enriched security event. It is a raw {@link DataLogRecord}
 * plus the three enrichment fields the assignment mandates — attackType,
 * threatScore, and receivedAt. Always constructed fully enriched and immutable.
 *
 * <p>Convenience accessors expose the most-queried underlying fields in the
 * ubiquitous language so callers rarely need to reach through the record.
 */
public record SecurityEvent(
        DataLogRecord dataLogRecord,
        AttackType attackType,
        ThreatScore threatScore,
        Instant receivedAt
) {
    public SecurityEvent {
        if (dataLogRecord == null) {
            throw new IllegalArgumentException("dataLogRecord must not be null");
        }
        if (attackType == null) {
            throw new IllegalArgumentException("attackType must not be null");
        }
        if (threatScore == null) {
            throw new IllegalArgumentException("threatScore must not be null");
        }
        if (receivedAt == null) {
            throw new IllegalArgumentException("receivedAt must not be null");
        }
    }

    public String eventId() {
        return dataLogRecord.eventId();
    }

    public int configId() {
        return dataLogRecord.configId();
    }

    public ClientIp clientIp() {
        return dataLogRecord.clientIp();
    }

    public Instant timestamp() {
        return dataLogRecord.timestamp();
    }

    public String path() {
        return dataLogRecord.path();
    }

    public Action action() {
        return dataLogRecord.action();
    }

    public AttackCategory category() {
        return dataLogRecord.rule().category();
    }
}
