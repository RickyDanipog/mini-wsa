package com.akamai.wsa.enrichment.ruleengine;

import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class RuleOperatorTest {

    private boolean matches(Map<String, Object> facts, String factKey, RuleOperator operator, String operand) {
        return new RuleCondition(factKey, operator, operand).matches(facts);
    }

    @Test
    void equalToComparesStrings() {
        assertThat(matches(Map.of("severity", "CRITICAL"), "severity", RuleOperator.EQUAL_TO, "CRITICAL")).isTrue();
        assertThat(matches(Map.of("severity", "LOW"), "severity", RuleOperator.EQUAL_TO, "CRITICAL")).isFalse();
    }

    @Test
    void notEqualToComparesStrings() {
        assertThat(matches(Map.of("action", "DENY"), "action", RuleOperator.NOT_EQUAL_TO, "ALERT")).isTrue();
        assertThat(matches(Map.of("action", "ALERT"), "action", RuleOperator.NOT_EQUAL_TO, "ALERT")).isFalse();
    }

    @Test
    void numericComparisonsCoerceBothSides() {
        assertThat(matches(Map.of("count", 6L), "count", RuleOperator.GREATER_THAN, "5")).isTrue();
        assertThat(matches(Map.of("count", 5L), "count", RuleOperator.GREATER_THAN, "5")).isFalse();
        assertThat(matches(Map.of("count", 5), "count", RuleOperator.GREATER_THAN_OR_EQUAL, "5")).isTrue();
        assertThat(matches(Map.of("count", 4), "count", RuleOperator.LESS_THAN, "5")).isTrue();
        assertThat(matches(Map.of("count", 5), "count", RuleOperator.LESS_THAN_OR_EQUAL, "5")).isTrue();
        assertThat(matches(Map.of("count", 10), "count", RuleOperator.EQUAL_TO, "10")).isTrue();
    }

    @Test
    void inMatchesAnyToken() {
        assertThat(matches(Map.of("method", "POST"), "method", RuleOperator.IN, "GET, POST, PUT")).isTrue();
        assertThat(matches(Map.of("method", "DELETE"), "method", RuleOperator.IN, "GET, POST, PUT")).isFalse();
    }

    @Test
    void notInNegatesTokenMembership() {
        assertThat(matches(Map.of("method", "DELETE"), "method", RuleOperator.NOT_IN, "GET, POST")).isTrue();
        assertThat(matches(Map.of("method", "GET"), "method", RuleOperator.NOT_IN, "GET, POST")).isFalse();
    }

    @Test
    void containsAnyMatchesSubstringOfAnyToken() {
        assertThat(matches(Map.of("path", "/api/v1/login"), "path", RuleOperator.CONTAINS_ANY, "/admin,/login")).isTrue();
        assertThat(matches(Map.of("path", "/admin/users"), "path", RuleOperator.CONTAINS_ANY, "/admin,/login")).isTrue();
        assertThat(matches(Map.of("path", "/public/home"), "path", RuleOperator.CONTAINS_ANY, "/admin,/login")).isFalse();
    }

    @Test
    void stringOperators() {
        assertThat(matches(Map.of("path", "/admin/users"), "path", RuleOperator.CONTAINS, "/admin")).isTrue();
        assertThat(matches(Map.of("path", "/admin/users"), "path", RuleOperator.STARTS_WITH, "/admin")).isTrue();
        assertThat(matches(Map.of("path", "/admin/users"), "path", RuleOperator.ENDS_WITH, "/users")).isTrue();
        assertThat(matches(Map.of("path", "/admin/users"), "path", RuleOperator.REGEX_MATCH, "/admin/.*")).isTrue();
        assertThat(matches(Map.of("path", "/public"), "path", RuleOperator.STARTS_WITH, "/admin")).isFalse();
    }

    @Test
    void betweenIsInclusiveNumeric() {
        assertThat(matches(Map.of("statusCode", 404), "statusCode", RuleOperator.BETWEEN, "400,499")).isTrue();
        assertThat(matches(Map.of("statusCode", 400), "statusCode", RuleOperator.BETWEEN, "400,499")).isTrue();
        assertThat(matches(Map.of("statusCode", 499), "statusCode", RuleOperator.BETWEEN, "400,499")).isTrue();
        assertThat(matches(Map.of("statusCode", 500), "statusCode", RuleOperator.BETWEEN, "400,499")).isFalse();
    }

    @Test
    void existsChecksPresence() {
        assertThat(matches(Map.of("clientIp", "1.2.3.4"), "clientIp", RuleOperator.EXISTS, "true")).isTrue();
        assertThat(matches(Map.of(), "clientIp", RuleOperator.EXISTS, "true")).isFalse();
        assertThat(matches(Map.of(), "clientIp", RuleOperator.EXISTS, "false")).isTrue();
        assertThat(matches(Map.of("clientIp", "1.2.3.4"), "clientIp", RuleOperator.EXISTS, "false")).isFalse();
    }

    @Test
    void missingFactIsFalseForNonExistsOperators() {
        assertThat(matches(Map.of(), "severity", RuleOperator.EQUAL_TO, "CRITICAL")).isFalse();
        assertThat(matches(Map.of(), "count", RuleOperator.GREATER_THAN, "5")).isFalse();
        assertThat(matches(Map.of(), "path", RuleOperator.CONTAINS_ANY, "/admin")).isFalse();
    }

    @Test
    void invalidRegexIsFalse() {
        assertThat(matches(Map.of("path", "/admin"), "path", RuleOperator.REGEX_MATCH, "[")).isFalse();
    }
}
