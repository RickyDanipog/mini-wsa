package com.akamai.wsa.enrichment.ruleengine;

public record RuleCondition(String factKey, RuleOperator operator, String operand) {

    public boolean matches(Facts facts) {
        return operator.test(facts.valueOf(factKey), operand);
    }
}
