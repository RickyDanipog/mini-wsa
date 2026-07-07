package com.akamai.wsa.enrichment.domain.model;

/** Value object: an integer risk score constrained to 0–100 (the scoring matrix cap). */
public record ThreatScore(int value) {
    public static final int MINIMUM = 0;
    public static final int MAXIMUM = 100;

    public ThreatScore {
        if (value < MINIMUM || value > MAXIMUM) {
            throw new IllegalArgumentException("threatScore out of range: " + value);
        }
    }

    /** Clamps a raw (possibly over-cap) score into the valid 0–100 range. */
    public static ThreatScore ofCapped(int rawScore) {
        return new ThreatScore(Math.max(MINIMUM, Math.min(MAXIMUM, rawScore)));
    }
}
