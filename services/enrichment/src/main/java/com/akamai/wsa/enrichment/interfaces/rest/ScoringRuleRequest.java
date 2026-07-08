package com.akamai.wsa.enrichment.interfaces.rest;

public record ScoringRuleRequest(
        String id,
        String title,
        String factKey,
        String operator,
        String operand,
        int output,
        int priority,
        boolean enabled) {
}
