package com.akamai.wsa.enrichment.domain.service;

public final class SeverityRule implements ScoringRule {
    public static final int CRITICAL_POINTS = 40;
    public static final int HIGH_POINTS = 30;
    public static final int MEDIUM_POINTS = 20;
    public static final int LOW_POINTS = 10;

    @Override
    public int points(ThreatScoringInputs threatScoringInputs) {
        return switch (threatScoringInputs.severity()) {
            case CRITICAL -> CRITICAL_POINTS;
            case HIGH -> HIGH_POINTS;
            case MEDIUM -> MEDIUM_POINTS;
            case LOW -> LOW_POINTS;
        };
    }
}
