package com.akamai.wsa.domain.service;

import com.akamai.wsa.domain.model.Action;
import com.akamai.wsa.domain.model.Severity;

/**
 * The complete, self-contained set of inputs the threat score depends on.
 * The stateful "repeat offender" signal is resolved by the caller and passed
 * in as a boolean, keeping {@link ThreatScoreCalculator} a pure function.
 */
public record ThreatScoringInputs(
        Severity severity,
        Action action,
        String requestPath,
        boolean repeatOffender
) {
    public ThreatScoringInputs {
        if (severity == null) {
            throw new IllegalArgumentException("severity must not be null");
        }
        if (action == null) {
            throw new IllegalArgumentException("action must not be null");
        }
        if (requestPath == null) {
            throw new IllegalArgumentException("requestPath must not be null");
        }
    }
}
