package com.akamai.wsa.enrichment.domain.service;

public final class RepeatOffenderRule implements ScoringRule {
    public static final int REPEAT_OFFENDER_POINTS = 15;

    @Override
    public int points(ThreatScoringInputs threatScoringInputs) {
        return threatScoringInputs.repeatOffender() ? REPEAT_OFFENDER_POINTS : 0;
    }
}
