package com.akamai.wsa.analytics.application.alert;

import com.akamai.wsa.analytics.domain.alert.AlertRule;
import com.akamai.wsa.analytics.domain.alert.AlertRuleStore;
import com.akamai.wsa.contracts.AttackCategory;
import org.springframework.stereotype.Service;

import java.util.UUID;

@Service
public class DefineAlertRuleService implements DefineAlertRule {

    private final AlertRuleStore alertRuleStore;

    public DefineAlertRuleService(AlertRuleStore alertRuleStore) {
        this.alertRuleStore = alertRuleStore;
    }

    @Override
    public AlertRule define(AttackCategory category, int threshold, int windowMinutes) {
        AlertRule alertRule = new AlertRule(UUID.randomUUID().toString(), category, threshold, windowMinutes);
        return alertRuleStore.save(alertRule);
    }
}
