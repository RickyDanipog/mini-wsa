package com.akamai.wsa.analytics.infrastructure.alert;

import com.akamai.wsa.analytics.domain.alert.AlertRule;
import com.akamai.wsa.analytics.domain.alert.AlertRuleStore;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

@Component
public class InMemoryAlertRuleStore implements AlertRuleStore {

    private final ConcurrentMap<String, AlertRule> rulesById = new ConcurrentHashMap<>();

    @Override
    public AlertRule save(AlertRule alertRule) {
        rulesById.put(alertRule.id(), alertRule);
        return alertRule;
    }

    @Override
    public List<AlertRule> findAll() {
        return List.copyOf(rulesById.values());
    }
}
