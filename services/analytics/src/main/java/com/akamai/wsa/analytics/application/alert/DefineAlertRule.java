package com.akamai.wsa.analytics.application.alert;

import com.akamai.wsa.analytics.domain.alert.AlertRule;
import com.akamai.wsa.contracts.AttackCategory;

public interface DefineAlertRule {
    AlertRule define(AttackCategory category, int threshold, int windowMinutes);
}
