package com.akamai.wsa;

final class ThreatScoring {
    private static final int SENSITIVE_PATH_POINTS = 15;
    private static final int REPEAT_OFFENDER_POINTS = 15;
    private static final int MAX_SCORE = 100;

    private ThreatScoring() {}

    static int score(SecurityEvent.Severity severity, SecurityEvent.Action action, String path,
                     boolean repeatOffender) {
        int severityPoints = switch (severity) {
            case CRITICAL -> 40;
            case HIGH -> 30;
            case MEDIUM -> 20;
            case LOW -> 10;
        };
        int actionPoints = switch (action) {
            case DENY -> 20;
            case ALERT -> 10;
            case MONITOR -> 0;
        };
        int score = severityPoints + actionPoints;
        if (path.contains("/admin") || path.contains("/login")) {
            score += SENSITIVE_PATH_POINTS;
        }
        if (repeatOffender) {
            score += REPEAT_OFFENDER_POINTS;
        }
        return Math.min(MAX_SCORE, score);
    }
}
