package com.akamai.wsa.analytics.domain.query;

import org.junit.jupiter.api.Test;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class IntervalTest {

    @Test
    void fromLabelMapsSupportedLabels() {
        assertThat(Interval.fromLabel("1m")).isEqualTo(Interval.ONE_MINUTE);
        assertThat(Interval.fromLabel("5m")).isEqualTo(Interval.FIVE_MINUTES);
        assertThat(Interval.fromLabel("1h")).isEqualTo(Interval.ONE_HOUR);
    }

    @Test
    void exposesLabelAndDuration() {
        assertThat(Interval.ONE_MINUTE.label()).isEqualTo("1m");
        assertThat(Interval.ONE_MINUTE.duration()).isEqualTo(Duration.ofMinutes(1));
        assertThat(Interval.FIVE_MINUTES.duration()).isEqualTo(Duration.ofMinutes(5));
        assertThat(Interval.ONE_HOUR.duration()).isEqualTo(Duration.ofHours(1));
    }

    @Test
    void fromLabelRejectsUnknownLabel() {
        assertThatThrownBy(() -> Interval.fromLabel("nope"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("invalid interval: nope (allowed: 1m, 5m, 1h)");
    }
}
