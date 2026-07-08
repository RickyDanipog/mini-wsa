package com.akamai.wsa.enrichment.ruleengine;

public record RuleCondition(String factKey, RuleOperator operator, String operand) {
}
