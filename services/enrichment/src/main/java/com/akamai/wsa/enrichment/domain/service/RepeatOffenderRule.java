package com.akamai.wsa.enrichment.domain.service;

/** Adds 15 points when the client is a repeat offender (flag injected by the caller). */
public final class RepeatOffenderRule implements ScoringRule {
    public static final int REPEAT_OFFENDER_POINTS = 15;

    @Override
    public int points(ThreatScoringInputs threatScoringInputs) {
        return threatScoringInputs.repeatOffender() ? REPEAT_OFFENDER_POINTS : 0;
    }
}
