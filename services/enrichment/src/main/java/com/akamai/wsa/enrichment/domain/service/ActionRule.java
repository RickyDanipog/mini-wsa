package com.akamai.wsa.enrichment.domain.service;

public final class ActionRule implements ScoringRule {
    public static final int DENY_POINTS = 20;
    public static final int ALERT_POINTS = 10;
    public static final int MONITOR_POINTS = 0;

    @Override
    public int points(ThreatScoringInputs threatScoringInputs) {
        return switch (threatScoringInputs.action()) {
            case DENY -> DENY_POINTS;
            case ALERT -> ALERT_POINTS;
            case MONITOR -> MONITOR_POINTS;
        };
    }
}
