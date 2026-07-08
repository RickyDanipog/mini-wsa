package com.akamai.wsa.enrichment.ruleengine;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class RuleEngineTest {

    private final RuleEngine ruleEngine = new RuleEngine(new RuleEvaluator());

    private Rule<Integer> rule(String id, int priority, boolean enabled, RuleCondition condition) {
        return new Rule<>(id, "SCORING", id, priority, enabled, condition, 1);
    }

    @Test
    void returnsMatchingEnabledRulesOrderedByPriority() {
        Rule<Integer> low = rule("low-priority", 30, true,
                new RuleCondition("severity", RuleOperator.EQUAL_TO, "CRITICAL"));
        Rule<Integer> high = rule("high-priority", 10, true,
                new RuleCondition("action", RuleOperator.EQUAL_TO, "DENY"));
        Map<String, Object> facts = Map.of("severity", "CRITICAL", "action", "DENY");

        List<Rule<Integer>> matching = ruleEngine.matchingRules(facts, List.of(low, high));

        assertThat(matching).extracting(Rule::id).containsExactly("high-priority", "low-priority");
    }

    @Test
    void excludesNonMatchingRules() {
        Rule<Integer> matching = rule("matching", 10, true,
                new RuleCondition("severity", RuleOperator.EQUAL_TO, "CRITICAL"));
        Rule<Integer> nonMatching = rule("non-matching", 20, true,
                new RuleCondition("severity", RuleOperator.EQUAL_TO, "LOW"));

        List<Rule<Integer>> result = ruleEngine.matchingRules(
                Map.of("severity", "CRITICAL"), List.of(matching, nonMatching));

        assertThat(result).extracting(Rule::id).containsExactly("matching");
    }

    @Test
    void excludesDisabledRules() {
        Rule<Integer> disabled = rule("disabled", 10, false,
                new RuleCondition("severity", RuleOperator.EQUAL_TO, "CRITICAL"));

        List<Rule<Integer>> result = ruleEngine.matchingRules(
                Map.of("severity", "CRITICAL"), List.of(disabled));

        assertThat(result).isEmpty();
    }
}
