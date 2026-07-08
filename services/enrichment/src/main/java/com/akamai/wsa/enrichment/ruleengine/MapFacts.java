package com.akamai.wsa.enrichment.ruleengine;

import java.util.Map;

public record MapFacts(Map<String, Object> values) implements Facts {

    @Override
    public Object valueOf(String key) {
        String[] segments = key.split("\\.");
        Object current = values;
        for (String segment : segments) {
            if (!(current instanceof Map<?, ?> currentMap)) {
                return null;
            }
            current = currentMap.get(segment);
        }
        return current;
    }
}
