package com.akamai.wsa.enrichment.domain.service;

import com.akamai.wsa.enrichment.domain.model.ThreatScore;

public interface ThreatScoreCalculator {
    ThreatScore calculate(ScoringFacts facts);
}
