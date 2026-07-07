package com.akamai.wsa.eventstore.infrastructure.persistence.mongo;

import com.akamai.wsa.eventstore.domain.model.StoredEvent;

public final class StoredEventDocumentMapper {

    private StoredEventDocumentMapper() {
    }

    public static StoredEventDocument toDocument(StoredEvent storedEvent) {
        return new StoredEventDocument(
                storedEvent.eventId(),
                storedEvent.eventId(),
                storedEvent.timestamp(),
                storedEvent.configId(),
                storedEvent.policyId(),
                storedEvent.clientIp(),
                storedEvent.hostname(),
                storedEvent.path(),
                storedEvent.method(),
                storedEvent.statusCode(),
                storedEvent.userAgent(),
                storedEvent.rule(),
                storedEvent.action(),
                storedEvent.geoLocation(),
                storedEvent.requestSize(),
                storedEvent.responseSize(),
                storedEvent.attackType(),
                storedEvent.threatScore(),
                storedEvent.receivedAt());
    }

    public static StoredEvent toStoredEvent(StoredEventDocument document) {
        return new StoredEvent(
                document.eventId(),
                document.timestamp(),
                document.configId(),
                document.policyId(),
                document.clientIp(),
                document.hostname(),
                document.path(),
                document.method(),
                document.statusCode(),
                document.userAgent(),
                document.rule(),
                document.action(),
                document.geoLocation(),
                document.requestSize(),
                document.responseSize(),
                document.attackType(),
                document.threatScore(),
                document.receivedAt());
    }
}
