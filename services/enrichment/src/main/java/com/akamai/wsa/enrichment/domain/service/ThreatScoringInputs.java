package com.akamai.wsa.enrichment.domain.service;

import com.akamai.wsa.contracts.Action;
import com.akamai.wsa.contracts.Severity;

public record ThreatScoringInputs(Severity severity, Action action, String requestPath, boolean repeatOffender) {
    public ThreatScoringInputs {
        if (severity == null || action == null || requestPath == null) {
            throw new IllegalArgumentException("severity, action, requestPath must not be null");
        }
    }
}
