package com.akamai.wsa.analytics.application.alert;

import com.akamai.wsa.analytics.domain.alert.AlertEvaluation;
import com.akamai.wsa.analytics.domain.alert.AlertRule;
import com.akamai.wsa.analytics.domain.alert.AlertRuleStore;
import com.akamai.wsa.analytics.domain.port.AnalyticsReadStore;
import com.akamai.wsa.analytics.domain.query.TimeRange;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;

@Service
public class EvaluateAlertsService implements EvaluateAlerts {

    private final AlertRuleStore alertRuleStore;
    private final AnalyticsReadStore readStore;
    private final Clock clock;

    public EvaluateAlertsService(AlertRuleStore alertRuleStore, AnalyticsReadStore readStore, Clock clock) {
        this.alertRuleStore = alertRuleStore;
        this.readStore = readStore;
        this.clock = clock;
    }

    @Override
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
