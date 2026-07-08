package com.akamai.wsa.enrichment.domain.service;

import com.akamai.wsa.enrichment.ruleengine.Facts;

public record ScoringFacts(
        String severity,
        String action,
        String category,
        String path,
        String method,
        int statusCode,
        String clientIp,
        long offenderEventCount) implements Facts {

    @Override
    public Object valueOf(String key) {
        return switch (key) {
            case FactKey.SEVERITY -> severity;
            case FactKey.ACTION -> action;
            case FactKey.CATEGORY -> category;
            case FactKey.PATH -> path;
            case FactKey.METHOD -> method;
            case FactKey.STATUS_CODE -> statusCode;
            case FactKey.CLIENT_IP -> clientIp;
            case FactKey.OFFENDER_EVENT_COUNT -> offenderEventCount;
            default -> null;
        };
    }
}
