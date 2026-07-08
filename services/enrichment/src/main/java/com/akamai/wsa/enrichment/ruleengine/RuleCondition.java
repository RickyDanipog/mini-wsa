package com.akamai.wsa.enrichment.ruleengine;

import java.util.Map;

public record RuleCondition(String factKey, RuleOperator operator, String operand) {

    public boolean matches(Map<String, Object> facts) {
        return operator.test(facts.get(factKey), operand);
    }
}
