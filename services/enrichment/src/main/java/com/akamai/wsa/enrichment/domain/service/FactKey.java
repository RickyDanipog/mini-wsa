package com.akamai.wsa.enrichment.domain.service;

import java.util.List;

public final class FactKey {

    public static final String SEVERITY = "rule.severity";
    public static final String CATEGORY = "rule.category";
    public static final String ACTION = "action";
    public static final String PATH = "path";
    public static final String METHOD = "method";
    public static final String STATUS_CODE = "statusCode";
    public static final String CLIENT_IP = "clientIp";
    public static final String OFFENDER_EVENT_COUNT = "offenderEventCount";
    public static final String GEO_COUNTRY = "geoLocation.country";

    public static List<String> all() {
        return List.of(SEVERITY, CATEGORY, ACTION, PATH, METHOD, STATUS_CODE,
                CLIENT_IP, OFFENDER_EVENT_COUNT, GEO_COUNTRY);
    }

    private FactKey() {
    }
}
