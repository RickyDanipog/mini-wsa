package com.akamai.wsa.analytics.domain.query;

import java.time.Instant;

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
