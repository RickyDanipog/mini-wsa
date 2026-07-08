package com.akamai.wsa.enrichment.infrastructure.rules;

import com.akamai.wsa.enrichment.domain.port.ScoringRuleRepository;
import com.akamai.wsa.enrichment.ruleengine.Rule;
import com.akamai.wsa.enrichment.ruleengine.RuleCondition;
import com.akamai.wsa.enrichment.ruleengine.RuleOperator;

import java.util.List;

public final class DefaultScoringRules {

    private DefaultScoringRules() {
    }

    public static List<Rule<Integer>> asList() {
        return List.of(
                scoringRule("severity-critical", "Critical severity", "severity", RuleOperator.EQUAL_TO, "CRITICAL", 40, 10),
                scoringRule("severity-high", "High severity", "severity", RuleOperator.EQUAL_TO, "HIGH", 30, 10),
                scoringRule("severity-medium", "Medium severity", "severity", RuleOperator.EQUAL_TO, "MEDIUM", 20, 10),
                scoringRule("severity-low", "Low severity", "severity", RuleOperator.EQUAL_TO, "LOW", 10, 10),
                scoringRule("action-deny", "Action deny", "action", RuleOperator.EQUAL_TO, "DENY", 20, 20),
                scoringRule("action-alert", "Action alert", "action", RuleOperator.EQUAL_TO, "ALERT", 10, 20),
                scoringRule("sensitive-path", "Sensitive path", "path", RuleOperator.CONTAINS_ANY, "/admin,/login", 15, 30),
                scoringRule("repeat-offender", "Repeat offender", "offenderEventCount", RuleOperator.GREATER_THAN, "5", 15, 40));
    }

    private static Rule<Integer> scoringRule(String id, String title, String factKey,
                                             RuleOperator operator, String operand, int points, int priority) {
        return new Rule<>(id, ScoringRuleRepository.SCORING_TYPE, title, priority, true,
                new RuleCondition(factKey, operator, operand), points);
    }
}
