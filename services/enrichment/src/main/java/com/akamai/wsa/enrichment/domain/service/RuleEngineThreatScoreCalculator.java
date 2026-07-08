package com.akamai.wsa.enrichment.domain.service;

import com.akamai.wsa.enrichment.domain.model.ThreatScore;
import com.akamai.wsa.enrichment.domain.port.ScoringRuleRepository;
import com.akamai.wsa.enrichment.ruleengine.Rule;
import com.akamai.wsa.enrichment.ruleengine.RuleEngine;

import java.util.Map;

public final class RuleEngineThreatScoreCalculator implements ThreatScoreCalculator {

    private final ScoringRuleRepository scoringRuleRepository;

    public RuleEngineThreatScoreCalculator(ScoringRuleRepository scoringRuleRepository) {
        this.scoringRuleRepository = scoringRuleRepository;
    }

    @Override
    public ThreatScore calculate(Map<String, Object> facts) {
        RuleEngine<Integer> engine = new RuleEngine<>(scoringRuleRepository.findEnabledRules());
        int total = engine.matching(facts).stream()
                .mapToInt(Rule::output)
                .sum();
        return ThreatScore.ofCapped(total);
    }
}
