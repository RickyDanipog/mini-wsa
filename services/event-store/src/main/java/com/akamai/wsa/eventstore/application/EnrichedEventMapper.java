package com.akamai.wsa.eventstore.application;

import com.akamai.wsa.contracts.EnrichedEventMessage;
import com.akamai.wsa.contracts.RawEventMessage;
import com.akamai.wsa.eventstore.domain.model.StoredEvent;
import com.akamai.wsa.eventstore.domain.model.StoredGeoLocation;
import com.akamai.wsa.eventstore.domain.model.StoredRule;

public final class EnrichedEventMapper {

    private EnrichedEventMapper() {
    }

    public static StoredEvent toStoredEvent(EnrichedEventMessage enrichedEventMessage) {
        RawEventMessage rawEvent = enrichedEventMessage.rawEvent();
        return new StoredEvent(
                rawEvent.eventId(),
                rawEvent.timestamp(),
                rawEvent.configId(),
                rawEvent.policyId(),
                rawEvent.clientIp(),
                rawEvent.hostname(),
                rawEvent.path(),
                rawEvent.method(),
                rawEvent.statusCode(),
                rawEvent.userAgent(),
                new StoredRule(
                        rawEvent.rule().id(),
                        rawEvent.rule().name(),
                        rawEvent.rule().message(),
                        rawEvent.rule().severity(),
                        rawEvent.rule().category()),
                rawEvent.action(),
                new StoredGeoLocation(
                        rawEvent.geoLocation().country(),
                        rawEvent.geoLocation().city()),
                rawEvent.requestSize(),
                rawEvent.responseSize(),
                enrichedEventMessage.attackType(),
                enrichedEventMessage.threatScore(),
                enrichedEventMessage.receivedAt());
    }
}
