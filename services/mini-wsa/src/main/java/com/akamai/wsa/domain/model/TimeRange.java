package com.akamai.wsa.domain.model;

import java.time.Instant;

/**
 * An inclusive time range for queries. A null bound means "open-ended" on that
 * side, so {@code new TimeRange(null, null)} matches all time.
 */
public record TimeRange(Instant from, Instant to) {

    public static TimeRange unbounded() {
        return new TimeRange(null, null);
    }

    public boolean includes(Instant instant) {
        if (from != null && instant.isBefore(from)) {
            return false;
        }
        return to == null || !instant.isAfter(to);
    }
}
