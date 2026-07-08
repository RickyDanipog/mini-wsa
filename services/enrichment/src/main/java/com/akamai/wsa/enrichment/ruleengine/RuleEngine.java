package com.akamai.wsa.enrichment.ruleengine;

import java.util.Comparator;
import java.util.List;
import java.util.Map;

public final class RuleEngine {

    private final RuleEvaluator ruleEvaluator;

    public RuleEngine(RuleEvaluator ruleEvaluator) {
        this.ruleEvaluator = ruleEvaluator;
    }

    public <TOutput> List<Rule<TOutput>> matchingRules(Map<String, Object> facts, List<Rule<TOutput>> rules) {
        return rules.stream()
                .filter(Rule::enabled)
                .filter(rule -> ruleEvaluator.matches(facts, rule.condition()))
                .sorted(Comparator.comparingInt(Rule::priority))
                .toList();
    }
}
