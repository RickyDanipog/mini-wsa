package com.akamai.wsa.domain.model;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ThreatScoreTest {

    @Test
    void acceptsValuesInRange() {
        assertThat(new ThreatScore(0).value()).isZero();
        assertThat(new ThreatScore(100).value()).isEqualTo(100);
    }

    @Test
    void rejectsValuesOutOfRange() {
        assertThatThrownBy(() -> new ThreatScore(-1)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new ThreatScore(101)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void capsRawTotalsAtOneHundred() {
        assertThat(ThreatScore.ofCapped(130).value()).isEqualTo(100);
        assertThat(ThreatScore.ofCapped(-5).value()).isZero();
        assertThat(ThreatScore.ofCapped(75).value()).isEqualTo(75);
    }
}
