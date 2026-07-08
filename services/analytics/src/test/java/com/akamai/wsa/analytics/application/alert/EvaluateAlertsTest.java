package com.akamai.wsa.analytics.application.alert;

import com.akamai.wsa.analytics.domain.alert.AlertEvaluation;
import com.akamai.wsa.analytics.domain.alert.AlertRule;
import com.akamai.wsa.analytics.domain.alert.AlertRuleStore;
import com.akamai.wsa.analytics.infrastructure.alert.InMemoryAlertRuleStore;
import com.akamai.wsa.analytics.infrastructure.persistence.inmemory.InMemoryAnalyticsReadStore;
import com.akamai.wsa.contracts.Action;
import com.akamai.wsa.contracts.AttackCategory;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;

import static com.akamai.wsa.analytics.testsupport.EnrichedEventViews.view;
import static org.assertj.core.api.Assertions.assertThat;

class EvaluateAlertsTest {

    private static final Instant AS_OF = Instant.parse("2026-05-20T14:10:00Z");

    private final InMemoryAnalyticsReadStore readStore = new InMemoryAnalyticsReadStore(List.of(
            view("evt-i0", 14227, "203.0.113.42", "/api/v1/login", AttackCategory.INJECTION, Action.DENY, 80,
                    Instant.parse("2026-05-20T13:59:00Z")),
            view("evt-i1", 14227, "203.0.113.42", "/api/v1/login", AttackCategory.INJECTION, Action.DENY, 80,
                    Instant.parse("2026-05-20T14:01:00Z")),
            view("evt-i2", 14227, "203.0.113.42", "/api/v1/login", AttackCategory.INJECTION, Action.DENY, 80,
                    Instant.parse("2026-05-20T14:05:00Z")),
            view("evt-i3", 14227, "203.0.113.42", "/api/v1/login", AttackCategory.INJECTION, Action.DENY, 80,
                    Instant.parse("2026-05-20T14:09:00Z")),
            view("evt-b1", 14227, "198.51.100.7", "/admin", AttackCategory.BOT, Action.ALERT, 40,
                    Instant.parse("2026-05-20T14:00:00Z")),
            view("evt-x1", 14227, "192.0.2.11", "/search", AttackCategory.XSS, Action.MONITOR, 30,
                    Instant.parse("2026-05-20T14:08:00Z"))));

    private final AlertRuleStore ruleStore = new InMemoryAlertRuleStore();
    private final EvaluateAlerts evaluateAlerts = new EvaluateAlertsService(
            ruleStore, readStore, Clock.fixed(AS_OF, ZoneOffset.UTC));

    @Test
    void firesWhenCountReachesThreshold() {
        AlertRule rule = ruleStore.save(new AlertRule("injection-3", AttackCategory.INJECTION, 3, 10));

        AlertEvaluation evaluation = onlyEvaluation();

        assertThat(evaluation.ruleId()).isEqualTo(rule.id());
        assertThat(evaluation.count()).isEqualTo(3);
        assertThat(evaluation.firing()).isTrue();
        assertThat(evaluation.window().from()).isEqualTo(Instant.parse("2026-05-20T14:00:00Z"));
        assertThat(evaluation.window().to()).isEqualTo(AS_OF);
    }

    @Test
    void doesNotFireBelowThreshold() {
        ruleStore.save(new AlertRule("injection-4", AttackCategory.INJECTION, 4, 10));

        AlertEvaluation evaluation = onlyEvaluation();

        assertThat(evaluation.count()).isEqualTo(3);
        assertThat(evaluation.firing()).isFalse();
    }

    @Test
    void includesEventOnWindowStartBoundaryAndExcludesBefore() {
        ruleStore.save(new AlertRule("bot-1", AttackCategory.BOT, 1, 10));

        AlertEvaluation evaluation = onlyEvaluation();

        assertThat(evaluation.count()).isEqualTo(1);
        assertThat(evaluation.firing()).isTrue();
    }

    @Test
    void narrowerWindowExcludesOlderEvents() {
        ruleStore.save(new AlertRule("injection-narrow", AttackCategory.INJECTION, 3, 2));

        AlertEvaluation evaluation = onlyEvaluation();

        assertThat(evaluation.window().from()).isEqualTo(Instant.parse("2026-05-20T14:08:00Z"));
        assertThat(evaluation.count()).isEqualTo(1);
        assertThat(evaluation.firing()).isFalse();
    }

    @Test
    void evaluatesMultipleRulesIndependently() {
        ruleStore.save(new AlertRule("injection-3", AttackCategory.INJECTION, 3, 10));
        ruleStore.save(new AlertRule("bot-2", AttackCategory.BOT, 2, 10));
        ruleStore.save(new AlertRule("xss-1", AttackCategory.XSS, 1, 10));

        List<AlertEvaluation> evaluations = evaluateAlerts.evaluate(AS_OF);

        assertThat(evaluations).hasSize(3);
        assertThat(evaluations).anySatisfy(evaluation -> {
            assertThat(evaluation.category()).isEqualTo(AttackCategory.INJECTION);
            assertThat(evaluation.count()).isEqualTo(3);
            assertThat(evaluation.firing()).isTrue();
        });
        assertThat(evaluations).anySatisfy(evaluation -> {
            assertThat(evaluation.category()).isEqualTo(AttackCategory.BOT);
            assertThat(evaluation.count()).isEqualTo(1);
            assertThat(evaluation.firing()).isFalse();
        });
        assertThat(evaluations).anySatisfy(evaluation -> {
            assertThat(evaluation.category()).isEqualTo(AttackCategory.XSS);
            assertThat(evaluation.count()).isEqualTo(1);
            assertThat(evaluation.firing()).isTrue();
        });
    }

    @Test
    void defaultsAsOfToClockWhenNull() {
        ruleStore.save(new AlertRule("injection-3", AttackCategory.INJECTION, 3, 10));

        AlertEvaluation evaluation = evaluateAlerts.evaluate(null).getFirst();

        assertThat(evaluation.window().to()).isEqualTo(AS_OF);
        assertThat(evaluation.count()).isEqualTo(3);
    }

    private AlertEvaluation onlyEvaluation() {
        List<AlertEvaluation> evaluations = evaluateAlerts.evaluate(AS_OF);
        assertThat(evaluations).hasSize(1);
        return evaluations.getFirst();
    }
}
