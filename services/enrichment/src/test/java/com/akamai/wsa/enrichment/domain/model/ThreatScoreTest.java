package com.akamai.wsa.enrichment.domain.model;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ThreatScoreTest {

    @Test
    void acceptsValuesWithinRange() {
        assertThat(new ThreatScore(0).value()).isZero();
        assertThat(new ThreatScore(100).value()).isEqualTo(100);
        assertThat(new ThreatScore(75).value()).isEqualTo(75);
    }

    @Test
    void rejectsNegativeValue() {
        assertThatThrownBy(() -> new ThreatScore(-1)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void rejectsValueAboveMaximum() {
        assertThatThrownBy(() -> new ThreatScore(101)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void ofCappedClampsAboveMaximumToOneHundred() {
        assertThat(ThreatScore.ofCapped(150).value()).isEqualTo(100);
    }

    @Test
    void ofCappedClampsBelowMinimumToZero() {
        assertThat(ThreatScore.ofCapped(-10).value()).isZero();
    }

    @Test
    void ofCappedPreservesInRangeValue() {
        assertThat(ThreatScore.ofCapped(90).value()).isEqualTo(90);
    }
}
