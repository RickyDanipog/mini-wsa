package com.akamai.wsa.enrichment.domain.service;

import com.akamai.wsa.contracts.Action;
import com.akamai.wsa.contracts.Severity;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ScoringRulesTest {

    private static ThreatScoringInputs inputs(Severity severity, Action action, String path, boolean repeat) {
        return new ThreatScoringInputs(severity, action, path, repeat);
    }

    @Test
    void severityRuleScoresEveryTier() {
        SeverityRule rule = new SeverityRule();
        assertThat(rule.points(inputs(Severity.CRITICAL, Action.MONITOR, "/x", false))).isEqualTo(40);
        assertThat(rule.points(inputs(Severity.HIGH, Action.MONITOR, "/x", false))).isEqualTo(30);
        assertThat(rule.points(inputs(Severity.MEDIUM, Action.MONITOR, "/x", false))).isEqualTo(20);
        assertThat(rule.points(inputs(Severity.LOW, Action.MONITOR, "/x", false))).isEqualTo(10);
    }

    @Test
    void actionRuleScoresEveryAction() {
        ActionRule rule = new ActionRule();
        assertThat(rule.points(inputs(Severity.LOW, Action.DENY, "/x", false))).isEqualTo(20);
        assertThat(rule.points(inputs(Severity.LOW, Action.ALERT, "/x", false))).isEqualTo(10);
        assertThat(rule.points(inputs(Severity.LOW, Action.MONITOR, "/x", false))).isZero();
    }

    @Test
    void sensitivePathRuleMatchesAdminAndLoginOnly() {
        SensitivePathRule rule = new SensitivePathRule();
        assertThat(rule.points(inputs(Severity.LOW, Action.MONITOR, "/admin", false))).isEqualTo(15);
        assertThat(rule.points(inputs(Severity.LOW, Action.MONITOR, "/api/v1/login", false))).isEqualTo(15);
        assertThat(rule.points(inputs(Severity.LOW, Action.MONITOR, "/admin/users", false))).isEqualTo(15);
        assertThat(rule.points(inputs(Severity.LOW, Action.MONITOR, "/public/home", false))).isZero();
        assertThat(rule.points(inputs(Severity.LOW, Action.MONITOR, "/wp-admin/panel", false))).isZero();
    }

    @Test
    void repeatOffenderRuleScoresOnlyWhenFlagged() {
        RepeatOffenderRule rule = new RepeatOffenderRule();
        assertThat(rule.points(inputs(Severity.LOW, Action.MONITOR, "/x", true))).isEqualTo(15);
        assertThat(rule.points(inputs(Severity.LOW, Action.MONITOR, "/x", false))).isZero();
    }
}
