package com.akamai.wsa.enrichment.domain.service;

import com.akamai.wsa.enrichment.domain.model.ThreatScore;

import java.util.List;

public final class RuleBasedThreatScoreCalculator implements ThreatScoreCalculator {

    private final List<ScoringRule> scoringRules;

    public RuleBasedThreatScoreCalculator(List<ScoringRule> scoringRules) {
        this.scoringRules = List.copyOf(scoringRules);
    }

    @Override
    public ThreatScore calculate(ThreatScoringInputs threatScoringInputs) {
        int total = scoringRules.stream()
                .mapToInt(scoringRule -> scoringRule.points(threatScoringInputs))
                .sum();
        return ThreatScore.ofCapped(total);
    }
}
