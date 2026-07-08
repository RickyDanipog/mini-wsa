package com.akamai.wsa.enrichment.domain.service;

import com.akamai.wsa.contracts.RawEventMessage;
import com.akamai.wsa.enrichment.ruleengine.Facts;

public record EventFacts(RawEventMessage event, long offenderEventCount) implements Facts {

    @Override
    public Object valueOf(String key) {
        return switch (key) {
            case FactKey.SEVERITY -> event.rule().severity().name();
            case FactKey.ACTION -> event.action().name();
            case FactKey.CATEGORY -> event.rule().category().name();
            case FactKey.PATH -> event.path();
            case FactKey.METHOD -> event.method();
            case FactKey.STATUS_CODE -> event.statusCode();
            case FactKey.CLIENT_IP -> event.clientIp();
            case FactKey.OFFENDER_EVENT_COUNT -> offenderEventCount;
            default -> null;
        };
    }
}
