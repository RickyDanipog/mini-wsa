package com.akamai.wsa.analytics.interfaces.rest;

import com.akamai.wsa.analytics.domain.alert.AlertRule;

public record AlertRuleResponse(String id, String category, int threshold, int windowMinutes) {

    public static AlertRuleResponse from(AlertRule alertRule) {
        return new AlertRuleResponse(
                alertRule.id(), alertRule.category().name(), alertRule.threshold(), alertRule.windowMinutes());
    }
}
