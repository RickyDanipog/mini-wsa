package com.akamai.wsa.enrichment.domain.service;

import com.akamai.wsa.enrichment.domain.model.ThreatScore;

import java.util.Map;

public interface ThreatScoreCalculator {
    ThreatScore calculate(Map<String, Object> facts);
}
