package com.akamai.wsa.domain.service;

import com.akamai.wsa.domain.model.ThreatScore;

/**
 * Computes the 0-100 threat score from a fully-resolved set of inputs.
 * Implementations must be pure and deterministic.
 */
public interface ThreatScoreCalculator {

    ThreatScore calculate(ThreatScoringInputs threatScoringInputs);
}
