package com.akamai.wsa.domain.model;

import java.time.Duration;

/**
 * A rolling look-back window (e.g. the repeat-offender "last 10 minutes").
 * Positive-length by construction.
 */
public record TimeWindow(Duration length) {

    public TimeWindow {
        if (length == null || length.isNegative() || length.isZero()) {
            throw new IllegalArgumentException("time window length must be positive");
        }
    }

    public static TimeWindow ofMinutes(long minutes) {
        return new TimeWindow(Duration.ofMinutes(minutes));
    }
}
