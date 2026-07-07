package com.akamai.wsa.enrichment.domain.model;

public record ThreatScore(int value) {
    public static final int MINIMUM = 0;
    public static final int MAXIMUM = 100;

    public ThreatScore {
        if (value < MINIMUM || value > MAXIMUM) {
            throw new IllegalArgumentException("threatScore out of range: " + value);
        }
    }

    public static ThreatScore ofCapped(int rawScore) {
        return new ThreatScore(Math.max(MINIMUM, Math.min(MAXIMUM, rawScore)));
    }
}
