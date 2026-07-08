package com.akamai.wsa.enrichment.ruleengine;

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
    EXISTS
}
