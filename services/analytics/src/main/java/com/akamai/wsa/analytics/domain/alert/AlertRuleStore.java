package com.akamai.wsa.analytics.domain.alert;

import java.util.List;

public interface AlertRuleStore {
    AlertRule save(AlertRule alertRule);

    List<AlertRule> findAll();
}
