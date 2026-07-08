package com.akamai.wsa.enrichment.ruleengine;

public record Rule<TOutput>(
        String id,
        String type,
        String title,
        int priority,
        boolean enabled,
        RuleCondition condition,
        TOutput output) {

    public boolean matches(Facts facts) {
        return enabled && condition.matches(facts);
    }
}
