package com.akamai.wsa.enrichment.domain.service;

import com.akamai.wsa.enrichment.domain.port.ScoringRuleRepository;
import com.akamai.wsa.enrichment.infrastructure.rules.InMemoryScoringRuleRepository;
import com.akamai.wsa.enrichment.ruleengine.Facts;
import com.akamai.wsa.enrichment.ruleengine.Rule;
import com.akamai.wsa.enrichment.ruleengine.RuleCondition;
import com.akamai.wsa.enrichment.ruleengine.RuleOperator;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class DefaultRulesScoringTest {

    private final ThreatScoreCalculator calculator = new RuleEngineThreatScoreCalculator(
            new InMemoryScoringRuleRepository());

    private int score(String severity, String action, String path, long offenderEventCount) {
        Map<String, Object> values = new HashMap<>();
        values.put(FactKey.SEVERITY, severity);
        values.put(FactKey.ACTION, action);
        values.put(FactKey.PATH, path);
        values.put(FactKey.OFFENDER_EVENT_COUNT, offenderEventCount);
        Facts facts = values::get;
        return calculator.calculate(facts).value();
    }

    @Test
    void reproducesHighAndLowMatrixCorners() {
        assertThat(score("CRITICAL", "DENY", "/api/v1/login", 6)).isEqualTo(90);
        assertThat(score("MEDIUM", "MONITOR", "/search", 0)).isEqualTo(20);
    }

    @Test
    void scoresEverySeverityTier() {
        assertThat(score("CRITICAL", "MONITOR", "/x", 0)).isEqualTo(40);
        assertThat(score("HIGH", "MONITOR", "/x", 0)).isEqualTo(30);
        assertThat(score("MEDIUM", "MONITOR", "/x", 0)).isEqualTo(20);
        assertThat(score("LOW", "MONITOR", "/x", 0)).isEqualTo(10);
    }

    @Test
    void scoresEveryAction() {
        assertThat(score("LOW", "DENY", "/x", 0)).isEqualTo(30);
        assertThat(score("LOW", "ALERT", "/x", 0)).isEqualTo(20);
        assertThat(score("LOW", "MONITOR", "/x", 0)).isEqualTo(10);
    }

    @Test
    void monitorActionAddsNothing() {
        assertThat(score("MEDIUM", "MONITOR", "/x", 0)).isEqualTo(20);
    }

    @Test
    void sensitivePathAddsFifteenForAdminAndLoginOnly() {
        assertThat(score("LOW", "MONITOR", "/admin", 0)).isEqualTo(25);
        assertThat(score("LOW", "MONITOR", "/api/v1/login", 0)).isEqualTo(25);
        assertThat(score("LOW", "MONITOR", "/admin/users", 0)).isEqualTo(25);
        assertThat(score("LOW", "MONITOR", "/public/home", 0)).isEqualTo(10);
    }

    @Test
    void repeatOffenderTriggersOnlyAboveFive() {
        assertThat(score("LOW", "MONITOR", "/x", 5)).isEqualTo(10);
        assertThat(score("LOW", "MONITOR", "/x", 6)).isEqualTo(25);
    }

    @Test
    void totalIsCappedAtOneHundred() {
        ScoringRuleRepository inflatedRepository = () -> List.of(
                new Rule<>("big", ScoringRuleRepository.SCORING_TYPE, "Big", 10, true,
                        new RuleCondition("severity", RuleOperator.EQUAL_TO, "CRITICAL"), 70),
                new Rule<>("bigger", ScoringRuleRepository.SCORING_TYPE, "Bigger", 20, true,
                        new RuleCondition("action", RuleOperator.EQUAL_TO, "DENY"), 50));
        ThreatScoreCalculator overflowing = new RuleEngineThreatScoreCalculator(inflatedRepository);

        Facts facts = Map.<String, Object>of(
                FactKey.SEVERITY, "CRITICAL", FactKey.ACTION, "DENY")::get;
        int total = overflowing.calculate(facts).value();

        assertThat(total).isEqualTo(100);
    }
}
