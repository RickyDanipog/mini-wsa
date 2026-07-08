package com.akamai.wsa.enrichment.infrastructure.rules;

import com.akamai.wsa.enrichment.domain.port.ScoringRuleRepository;
import com.akamai.wsa.enrichment.ruleengine.Rule;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Repository;

import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Repository
@ConditionalOnProperty(name = "wsa.rules", havingValue = "inmemory", matchIfMissing = true)
public class InMemoryScoringRuleRepository implements ScoringRuleRepository {

    private final Map<String, Rule<Integer>> rulesById = new ConcurrentHashMap<>();

    public InMemoryScoringRuleRepository() {
        DefaultScoringRules.asList().forEach(rule -> rulesById.put(rule.id(), rule));
    }

    @Override
    public List<Rule<Integer>> findEnabledRules() {
        return rulesById.values().stream()
                .filter(Rule::enabled)
                .sorted(Comparator.comparingInt(Rule::priority))
                .toList();
    }

    @Override
    public List<Rule<Integer>> findAll() {
        return rulesById.values().stream()
                .sorted(Comparator.comparingInt(Rule::priority))
                .toList();
    }

    @Override
    public Rule<Integer> save(Rule<Integer> rule) {
        rulesById.put(rule.id(), rule);
        return rule;
    }

    @Override
    public void deleteById(String id) {
        rulesById.remove(id);
    }
}
