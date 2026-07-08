package com.akamai.wsa.analytics.domain.query;

import java.time.Duration;

public enum Interval {
    ONE_MINUTE("1m", Duration.ofMinutes(1)),
    FIVE_MINUTES("5m", Duration.ofMinutes(5)),
    ONE_HOUR("1h", Duration.ofHours(1));

    private final String label;
    private final Duration duration;

    Interval(String label, Duration duration) {
        this.label = label;
        this.duration = duration;
    }

    public String label() {
        return label;
    }

    public Duration duration() {
        return duration;
    }

    public static Interval fromLabel(String requestedLabel) {
        for (Interval interval : values()) {
            if (interval.label.equals(requestedLabel)) {
                return interval;
            }
        }
        throw new IllegalArgumentException(
                "invalid interval: " + requestedLabel + " (allowed: 1m, 5m, 1h)");
    }
}
