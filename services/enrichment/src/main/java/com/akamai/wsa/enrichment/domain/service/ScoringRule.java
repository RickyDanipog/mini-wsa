package com.akamai.wsa.enrichment.domain.service;

/** One composable contributor to the total threat score (Logic-2 rule object). */
public interface ScoringRule {
    int points(ThreatScoringInputs threatScoringInputs);
}
