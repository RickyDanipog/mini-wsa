package com.akamai.wsa.analytics.application.alert;

import com.akamai.wsa.analytics.domain.alert.AlertEvaluation;
import com.akamai.wsa.analytics.domain.alert.AlertRule;
import com.akamai.wsa.analytics.domain.alert.AlertRuleStore;
import com.akamai.wsa.analytics.domain.port.AnalyticsReadStore;
import com.akamai.wsa.analytics.domain.query.TimeRange;
import com.akamai.wsa.contracts.AttackCategory;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Service
public class AlertService {

    private final AlertRuleStore alertRuleStore;
    private final AnalyticsReadStore readStore;
    private final Clock clock;

    public AlertService(AlertRuleStore alertRuleStore, AnalyticsReadStore readStore, Clock clock) {
        this.alertRuleStore = alertRuleStore;
        this.readStore = readStore;
        this.clock = clock;
    }

    public AlertRule defineRule(AttackCategory category, int threshold, int windowMinutes) {
        AlertRule alertRule = new AlertRule(UUID.randomUUID().toString(), category, threshold, windowMinutes);
        return alertRuleStore.save(alertRule);
    }

    public List<AlertEvaluation> evaluate(Instant asOf) {
        Instant effectiveAsOf = asOf != null ? asOf : Instant.now(clock);
        return alertRuleStore.findAll().stream()
                .map(rule -> evaluateRule(rule, effectiveAsOf))
                .toList();
    }

    private AlertEvaluation evaluateRule(AlertRule rule, Instant asOf) {
        TimeRange window = new TimeRange(asOf.minus(Duration.ofMinutes(rule.windowMinutes())), asOf);
        long count = readStore.countByCategoryWithin(rule.category(), window);
        boolean firing = count >= rule.threshold();
        return new AlertEvaluation(
                rule.id(), rule.category(), rule.threshold(), rule.windowMinutes(), count, firing, window);
    }
}
