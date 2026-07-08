package com.akamai.wsa.enrichment.ruleengine;

import java.util.Arrays;
import java.util.List;
import java.util.regex.PatternSyntaxException;

public enum RuleOperator {
    EQUAL_TO,
    NOT_EQUAL_TO,
    GREATER_THAN,
    GREATER_THAN_OR_EQUAL,
    LESS_THAN,
    LESS_THAN_OR_EQUAL,
    IN,
    NOT_IN,
    CONTAINS,
    CONTAINS_ANY,
    STARTS_WITH,
    ENDS_WITH,
    REGEX_MATCH,
    BETWEEN,
    EXISTS;

    public boolean test(Object factValue, String operand) {
        if (this == EXISTS) {
            return Boolean.parseBoolean(operand) == (factValue != null);
        }
        if (factValue == null) {
            return false;
        }

        String value = String.valueOf(factValue);
        return switch (this) {
            case EQUAL_TO -> compare(value, operand) == 0;
            case NOT_EQUAL_TO -> compare(value, operand) != 0;
            case GREATER_THAN -> compare(value, operand) > 0;
            case GREATER_THAN_OR_EQUAL -> compare(value, operand) >= 0;
            case LESS_THAN -> compare(value, operand) < 0;
            case LESS_THAN_OR_EQUAL -> compare(value, operand) <= 0;
            case IN -> tokens(operand).contains(value);
            case NOT_IN -> !tokens(operand).contains(value);
            case CONTAINS_ANY -> tokens(operand).stream().anyMatch(value::contains);
            case CONTAINS -> value.contains(operand);
            case STARTS_WITH -> value.startsWith(operand);
            case ENDS_WITH -> value.endsWith(operand);
            case REGEX_MATCH -> matchesRegex(value, operand);
            case BETWEEN -> between(value, operand);
            case EXISTS -> false;
        };
    }

    private static int compare(String factValue, String operand) {
        Double factNumber = asNumber(factValue);
        Double operandNumber = asNumber(operand);
        if (factNumber != null && operandNumber != null) {
            return Double.compare(factNumber, operandNumber);
        }
        return factValue.compareTo(operand);
    }

    private static boolean between(String factValue, String operand) {
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

    private static boolean matchesRegex(String factValue, String operand) {
        try {
            return factValue.matches(operand);
        } catch (PatternSyntaxException invalidPattern) {
            return false;
        }
    }

    private static List<String> tokens(String operand) {
        return Arrays.stream(operand.split(","))
                .map(String::trim)
                .toList();
    }

    private static Double asNumber(String value) {
        try {
            return Double.valueOf(value.trim());
        } catch (NumberFormatException notANumber) {
            return null;
        }
    }
}
