package com.akamai.wsa.enrichment.domain.service;

import com.akamai.wsa.enrichment.domain.model.ThreatScore;
import com.akamai.wsa.enrichment.ruleengine.Facts;

public interface ThreatScoreCalculator {
    ThreatScore calculate(Facts facts);
}
