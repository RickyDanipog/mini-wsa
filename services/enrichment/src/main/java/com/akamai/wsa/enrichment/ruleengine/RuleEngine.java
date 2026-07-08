package com.akamai.wsa.enrichment.ruleengine;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;

public final class RuleEngine<TOutput> {

    private final List<Rule<TOutput>> rules = new ArrayList<>();

    public RuleEngine() {
    }

    public RuleEngine(List<Rule<TOutput>> initialRules) {
        initialRules.forEach(this::register);
    }

    public void register(Rule<TOutput> rule) {
        rules.add(rule);
        rules.sort(Comparator.comparingInt(Rule::priority));
    }

    public List<Rule<TOutput>> matching(Map<String, Object> facts) {
        return rules.stream()
                .filter(rule -> rule.matches(facts))
                .toList();
    }

    public Optional<Rule<TOutput>> evaluate(Map<String, Object> facts) {
        return rules.stream()
                .filter(rule -> rule.matches(facts))
                .findFirst();
    }
}
