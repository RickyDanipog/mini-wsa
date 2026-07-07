package com.akamai.wsa.gateway.application;

import com.akamai.wsa.contracts.GeoLocationMessage;
import com.akamai.wsa.contracts.RawEventMessage;
import com.akamai.wsa.contracts.RuleMessage;
import com.akamai.wsa.gateway.interfaces.rest.dto.GeoLocationDto;
import com.akamai.wsa.gateway.interfaces.rest.dto.IngestEventRequest;
import com.akamai.wsa.gateway.interfaces.rest.dto.RuleDto;

/** Pure DTO to contract mapper. No framework, no I/O. */
public class EventRequestMapper {

    public RawEventMessage toRawEventMessage(IngestEventRequest ingestEventRequest) {
        return new RawEventMessage(
                ingestEventRequest.eventId(),
                ingestEventRequest.timestamp(),
                ingestEventRequest.configId(),
                ingestEventRequest.policyId(),
                ingestEventRequest.clientIp(),
                ingestEventRequest.hostname(),
                ingestEventRequest.path(),
                ingestEventRequest.method(),
                ingestEventRequest.statusCode(),
                ingestEventRequest.userAgent(),
                toRuleMessage(ingestEventRequest.rule()),
                ingestEventRequest.action(),
                toGeoLocationMessage(ingestEventRequest.geoLocation()),
                ingestEventRequest.requestSize(),
                ingestEventRequest.responseSize());
    }

    private RuleMessage toRuleMessage(RuleDto ruleDto) {
        return new RuleMessage(
                ruleDto.id(),
                ruleDto.name(),
                ruleDto.message(),
                ruleDto.severity(),
                ruleDto.category());
    }

    private GeoLocationMessage toGeoLocationMessage(GeoLocationDto geoLocationDto) {
        if (geoLocationDto == null) {
            return null;
        }
        return new GeoLocationMessage(geoLocationDto.country(), geoLocationDto.city());
    }
}
