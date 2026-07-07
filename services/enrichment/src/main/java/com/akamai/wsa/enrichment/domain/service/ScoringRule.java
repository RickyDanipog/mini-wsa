package com.akamai.wsa.enrichment.domain.service;

public interface ScoringRule {
    int points(ThreatScoringInputs threatScoringInputs);
}
