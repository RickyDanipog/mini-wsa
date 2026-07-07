package com.akamai.wsa.enrichment.domain.service;

import com.akamai.wsa.contracts.Action;
import com.akamai.wsa.contracts.Severity;

/**
 * Immutable inputs to the pure threat scorer. The {@code repeatOffender} flag is
 * computed by the caller (from the offender window) and injected here, keeping the
 * scorer a pure function.
 */
public record ThreatScoringInputs(Severity severity, Action action, String requestPath, boolean repeatOffender) {
    public ThreatScoringInputs {
        if (severity == null || action == null || requestPath == null) {
            throw new IllegalArgumentException("severity, action, requestPath must not be null");
        }
    }
}
