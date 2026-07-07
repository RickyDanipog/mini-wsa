package com.akamai.wsa.enrichment.infrastructure.window;

import com.akamai.wsa.enrichment.domain.port.OffenderWindow;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

class InMemoryOffenderWindowTest {

    private static final Duration TEN_MINUTES = Duration.ofMinutes(10);
    private static final Instant NOW = Instant.parse("2026-05-20T14:00:00Z");

    private final OffenderWindow window = new InMemoryOffenderWindow();

    @Test
    void countsEventsRecordedForClientWithinWindow() {
        for (int i = 0; i < 4; i++) {
            window.recordEvent("10.0.0.1", NOW);
        }
        assertThat(window.countRecentEventsFromClient("10.0.0.1", TEN_MINUTES, NOW)).isEqualTo(4);
    }

    @Test
    void isolatesCountsPerClient() {
        window.recordEvent("10.0.0.1", NOW);
        window.recordEvent("10.0.0.2", NOW);
        assertThat(window.countRecentEventsFromClient("10.0.0.1", TEN_MINUTES, NOW)).isEqualTo(1);
        assertThat(window.countRecentEventsFromClient("unknown", TEN_MINUTES, NOW)).isZero();
    }

    @Test
    void excludesEventsOlderThanWindow() {
        window.recordEvent("10.0.0.1", NOW.minus(Duration.ofMinutes(11)));
        window.recordEvent("10.0.0.1", NOW);
        assertThat(window.countRecentEventsFromClient("10.0.0.1", TEN_MINUTES, NOW)).isEqualTo(1);
    }

    @Test
    void includesEventAtExactlyTenMinuteBoundary() {
        window.recordEvent("10.0.0.1", NOW.minus(TEN_MINUTES));
        assertThat(window.countRecentEventsFromClient("10.0.0.1", TEN_MINUTES, NOW)).isEqualTo(1);
    }

    @Test
    void sixthEventInWindowIsFirstToCrossRepeatOffenderThreshold() {
        // Locked semantics: flag when count > 5, i.e. the 6th event within the window.
        for (int i = 1; i <= 5; i++) {
            window.recordEvent("10.0.0.1", NOW);
        }
        assertThat(window.countRecentEventsFromClient("10.0.0.1", TEN_MINUTES, NOW)).isEqualTo(5);

        window.recordEvent("10.0.0.1", NOW);
        assertThat(window.countRecentEventsFromClient("10.0.0.1", TEN_MINUTES, NOW)).isEqualTo(6);
    }
}
