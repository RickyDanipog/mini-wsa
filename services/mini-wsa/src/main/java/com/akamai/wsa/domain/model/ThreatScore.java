package com.akamai.wsa.domain.model;

/**
 * Value object for a computed threat score, constrained to the inclusive range [0, 100].
 */
public record ThreatScore(int value) {

    public static final int MINIMUM = 0;
    public static final int MAXIMUM = 100;

    public ThreatScore {
        if (value < MINIMUM || value > MAXIMUM) {
            throw new IllegalArgumentException("threatScore must be within [" + MINIMUM + ", " + MAXIMUM + "]");
        }
    }

    /**
     * Builds a threat score from a raw total, clamping it into the valid range
     * (the assignment's "cap at 100").
     */
    public static ThreatScore ofCapped(int rawScore) {
        return new ThreatScore(Math.max(MINIMUM, Math.min(MAXIMUM, rawScore)));
    }
}
