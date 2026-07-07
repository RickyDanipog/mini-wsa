package com.akamai.wsa.enrichment.domain.service;

import com.akamai.wsa.contracts.Action;
import com.akamai.wsa.contracts.Severity;
import com.akamai.wsa.enrichment.domain.model.ThreatScore;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.List;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

class RuleBasedThreatScoreCalculatorTest {

    private final ThreatScoreCalculator calculator = new RuleBasedThreatScoreCalculator(List.of(
            new SeverityRule(), new ActionRule(), new SensitivePathRule(), new RepeatOffenderRule()));

    private static Arguments matrixCase(Severity severity, Action action, String path, boolean repeat, int expected) {
        return Arguments.of(severity, action, path, repeat, expected);
    }

    static Stream<Arguments> gradedScoringMatrix() {
        return Stream.of(
                matrixCase(Severity.CRITICAL, Action.DENY, "/api/v1/login", false, 75),
                matrixCase(Severity.CRITICAL, Action.DENY, "/api/v1/login", true, 90),

                matrixCase(Severity.CRITICAL, Action.DENY, "/public", false, 60),
                matrixCase(Severity.HIGH, Action.DENY, "/public", false, 50),
                matrixCase(Severity.MEDIUM, Action.DENY, "/public", false, 40),
                matrixCase(Severity.LOW, Action.DENY, "/public", false, 30),

                matrixCase(Severity.LOW, Action.DENY, "/public", false, 30),
                matrixCase(Severity.LOW, Action.ALERT, "/public", false, 20),
                matrixCase(Severity.LOW, Action.MONITOR, "/public", false, 10),

                matrixCase(Severity.LOW, Action.MONITOR, "/public/home", false, 10),

                matrixCase(Severity.LOW, Action.MONITOR, "/admin", false, 25),
                matrixCase(Severity.LOW, Action.MONITOR, "/login", false, 25),
                matrixCase(Severity.LOW, Action.MONITOR, "/admin/users", false, 25),
                matrixCase(Severity.LOW, Action.MONITOR, "/public/home", false, 10),

                matrixCase(Severity.MEDIUM, Action.ALERT, "/public", true, 45),
                matrixCase(Severity.HIGH, Action.DENY, "/admin", true, 80),

                matrixCase(Severity.CRITICAL, Action.DENY, "/admin", true, 90)
        );
    }

    @ParameterizedTest
    @MethodSource("gradedScoringMatrix")
    void calculatesGradedScore(Severity severity, Action action, String path, boolean repeat, int expected) {
        ThreatScore score = calculator.calculate(new ThreatScoringInputs(severity, action, path, repeat));
        assertThat(score.value()).isEqualTo(expected);
    }

    @Test
    void capsTotalAtOneHundred() {
        ThreatScoreCalculator overflowing = new RuleBasedThreatScoreCalculator(List.of(
                inputs -> 70, inputs -> 50));
        ThreatScore score = overflowing.calculate(
                new ThreatScoringInputs(Severity.CRITICAL, Action.DENY, "/admin", true));
        assertThat(score.value()).isEqualTo(100);
    }
}
