package com.akamai.wsa.enrichment.domain.service;

import com.akamai.wsa.enrichment.domain.model.ThreatScore;
import com.akamai.wsa.enrichment.domain.port.ScoringRuleRepository;
import com.akamai.wsa.enrichment.ruleengine.Rule;
import com.akamai.wsa.enrichment.ruleengine.RuleEngine;

import java.util.Map;

public final class RuleEngineThreatScoreCalculator implements ThreatScoreCalculator {

    private final ScoringRuleRepository scoringRuleRepository;
    private final RuleEngine ruleEngine;

    public RuleEngineThreatScoreCalculator(ScoringRuleRepository scoringRuleRepository, RuleEngine ruleEngine) {
        this.scoringRuleRepository = scoringRuleRepository;
        this.ruleEngine = ruleEngine;
    }

    @Override
    public ThreatScore calculate(Map<String, Object> facts) {
        int total = ruleEngine.matchingRules(facts, scoringRuleRepository.findEnabledRules()).stream()
                .mapToInt(Rule::output)
                .sum();
        return ThreatScore.ofCapped(total);
    }
}
