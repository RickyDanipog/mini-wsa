package com.akamai.wsa.enrichment.ruleengine;

import java.util.Map;

public record Rule<TOutput>(
        String id,
        String type,
        String title,
        int priority,
        boolean enabled,
        RuleCondition condition,
        TOutput output) {

    public boolean matches(Map<String, Object> facts) {
        return enabled && condition.matches(facts);
    }
}
