package com.akamai.wsa.enrichment.domain.service;

import com.akamai.wsa.enrichment.domain.model.ThreatScore;

/** Pure function: turns scoring inputs into a capped {@link ThreatScore}. */
public interface ThreatScoreCalculator {
    ThreatScore calculate(ThreatScoringInputs threatScoringInputs);
}
