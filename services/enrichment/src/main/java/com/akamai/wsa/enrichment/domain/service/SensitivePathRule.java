package com.akamai.wsa.enrichment.domain.service;

public final class SensitivePathRule implements ScoringRule {
    public static final int SENSITIVE_PATH_POINTS = 15;

    @Override
    public int points(ThreatScoringInputs threatScoringInputs) {
        String requestPath = threatScoringInputs.requestPath();
        boolean sensitive = requestPath.contains("/admin") || requestPath.contains("/login");
        return sensitive ? SENSITIVE_PATH_POINTS : 0;
    }
}
