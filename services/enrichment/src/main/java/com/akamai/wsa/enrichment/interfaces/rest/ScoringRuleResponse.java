package com.akamai.wsa.enrichment.interfaces.rest;

import com.akamai.wsa.enrichment.ruleengine.Rule;
import com.akamai.wsa.enrichment.ruleengine.RuleCondition;

public record ScoringRuleResponse(
        String id,
        String title,
        String factKey,
        String operator,
        String operand,
        int output,
        int priority,
        boolean enabled) {

    public static ScoringRuleResponse from(Rule<Integer> rule) {
        RuleCondition condition = rule.condition();
        return new ScoringRuleResponse(
                rule.id(), rule.title(), condition.factKey(), condition.operator().name(),
                condition.operand(), rule.output(), rule.priority(), rule.enabled());
    }
}
