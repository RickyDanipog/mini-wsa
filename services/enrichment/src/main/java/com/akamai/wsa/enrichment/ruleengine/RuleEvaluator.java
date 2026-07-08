package com.akamai.wsa.enrichment.ruleengine;

import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.regex.PatternSyntaxException;

public final class RuleEvaluator {

    public boolean matches(Map<String, Object> facts, RuleCondition condition) {
        Object fact = facts.get(condition.factKey());
        RuleOperator operator = condition.operator();
        String operand = condition.operand();

        if (operator == RuleOperator.EXISTS) {
            return Boolean.parseBoolean(operand) == (fact != null);
        }
        if (fact == null) {
            return false;
        }

        String factValue = String.valueOf(fact);
        return switch (operator) {
            case EQUAL_TO -> compare(factValue, operand) == 0;
            case NOT_EQUAL_TO -> compare(factValue, operand) != 0;
            case GREATER_THAN -> compare(factValue, operand) > 0;
            case GREATER_THAN_OR_EQUAL -> compare(factValue, operand) >= 0;
            case LESS_THAN -> compare(factValue, operand) < 0;
            case LESS_THAN_OR_EQUAL -> compare(factValue, operand) <= 0;
            case IN -> tokens(operand).contains(factValue);
            case NOT_IN -> !tokens(operand).contains(factValue);
            case CONTAINS_ANY -> tokens(operand).stream().anyMatch(factValue::contains);
            case CONTAINS -> factValue.contains(operand);
            case STARTS_WITH -> factValue.startsWith(operand);
            case ENDS_WITH -> factValue.endsWith(operand);
            case REGEX_MATCH -> matchesRegex(factValue, operand);
            case BETWEEN -> between(factValue, operand);
            case EXISTS -> false;
        };
    }

    private int compare(String factValue, String operand) {
        Double factNumber = asNumber(factValue);
        Double operandNumber = asNumber(operand);
        if (factNumber != null && operandNumber != null) {
            return Double.compare(factNumber, operandNumber);
        }
        return factValue.compareTo(operand);
    }

    private boolean between(String factValue, String operand) {
        List<String> bounds = tokens(operand);
        if (bounds.size() != 2) {
            return false;
        }
        Double factNumber = asNumber(factValue);
        Double lowerBound = asNumber(bounds.get(0));
        Double upperBound = asNumber(bounds.get(1));
        if (factNumber == null || lowerBound == null || upperBound == null) {
            return false;
        }
        return factNumber >= lowerBound && factNumber <= upperBound;
    }

    private boolean matchesRegex(String factValue, String operand) {
        try {
            return factValue.matches(operand);
        } catch (PatternSyntaxException invalidPattern) {
            return false;
        }
    }

    private List<String> tokens(String operand) {
        return Arrays.stream(operand.split(","))
                .map(String::trim)
                .toList();
    }

    private Double asNumber(String value) {
        try {
            return Double.valueOf(value.trim());
        } catch (NumberFormatException notANumber) {
            return null;
        }
    }
}
