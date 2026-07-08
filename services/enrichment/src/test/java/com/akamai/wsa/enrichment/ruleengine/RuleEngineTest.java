package com.akamai.wsa.enrichment.ruleengine;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

class RuleEngineTest {

    private Rule<Integer> rule(String id, int priority, boolean enabled, RuleCondition condition) {
        return new Rule<>(id, "SCORING", id, priority, enabled, condition, 1);
    }

    private Facts facts(Map<String, Object> values) {
        return values::get;
    }

    @Test
    void matchingReturnsMatchingEnabledRulesOrderedByPriority() {
        Rule<Integer> low = rule("low-priority", 30, true,
                new RuleCondition("severity", RuleOperator.EQUAL_TO, "CRITICAL"));
        Rule<Integer> high = rule("high-priority", 10, true,
                new RuleCondition("action", RuleOperator.EQUAL_TO, "DENY"));
        RuleEngine<Integer> ruleEngine = new RuleEngine<>(List.of(low, high));
        Facts facts = facts(Map.of("severity", "CRITICAL", "action", "DENY"));

        List<Rule<Integer>> matching = ruleEngine.matching(facts);

        assertThat(matching).extracting(Rule::id).containsExactly("high-priority", "low-priority");
    }

    @Test
    void matchingExcludesNonMatchingRules() {
        Rule<Integer> matching = rule("matching", 10, true,
                new RuleCondition("severity", RuleOperator.EQUAL_TO, "CRITICAL"));
        Rule<Integer> nonMatching = rule("non-matching", 20, true,
                new RuleCondition("severity", RuleOperator.EQUAL_TO, "LOW"));
        RuleEngine<Integer> ruleEngine = new RuleEngine<>(List.of(matching, nonMatching));

        List<Rule<Integer>> result = ruleEngine.matching(facts(Map.of("severity", "CRITICAL")));

        assertThat(result).extracting(Rule::id).containsExactly("matching");
    }

    @Test
    void matchingExcludesDisabledRules() {
        Rule<Integer> disabled = rule("disabled", 10, false,
                new RuleCondition("severity", RuleOperator.EQUAL_TO, "CRITICAL"));
        RuleEngine<Integer> ruleEngine = new RuleEngine<>(List.of(disabled));

        List<Rule<Integer>> result = ruleEngine.matching(facts(Map.of("severity", "CRITICAL")));

        assertThat(result).isEmpty();
    }

    @Test
    void registerKeepsRulesOrderedByPriority() {
        RuleEngine<Integer> ruleEngine = new RuleEngine<>();
        ruleEngine.register(rule("low-priority", 30, true,
                new RuleCondition("severity", RuleOperator.EQUAL_TO, "CRITICAL")));
        ruleEngine.register(rule("high-priority", 10, true,
                new RuleCondition("action", RuleOperator.EQUAL_TO, "DENY")));

        List<Rule<Integer>> matching = ruleEngine.matching(facts(Map.of("severity", "CRITICAL", "action", "DENY")));

        assertThat(matching).extracting(Rule::id).containsExactly("high-priority", "low-priority");
    }

    @Test
    void evaluateReturnsFirstMatchByPriority() {
        Rule<Integer> low = rule("low-priority", 30, true,
                new RuleCondition("severity", RuleOperator.EQUAL_TO, "CRITICAL"));
        Rule<Integer> high = rule("high-priority", 10, true,
                new RuleCondition("action", RuleOperator.EQUAL_TO, "DENY"));
        RuleEngine<Integer> ruleEngine = new RuleEngine<>(List.of(low, high));

        Optional<Rule<Integer>> match = ruleEngine.evaluate(facts(Map.of("severity", "CRITICAL", "action", "DENY")));

        assertThat(match).map(Rule::id).contains("high-priority");
    }

    @Test
    void evaluateIsEmptyWhenNothingMatches() {
        Rule<Integer> rule = rule("matching", 10, true,
                new RuleCondition("severity", RuleOperator.EQUAL_TO, "CRITICAL"));
        RuleEngine<Integer> ruleEngine = new RuleEngine<>(List.of(rule));

        assertThat(ruleEngine.evaluate(facts(Map.of("severity", "LOW")))).isEmpty();
    }
}
