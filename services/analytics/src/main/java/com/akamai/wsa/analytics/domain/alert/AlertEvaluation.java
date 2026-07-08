package com.akamai.wsa.analytics.domain.alert;

import com.akamai.wsa.analytics.domain.query.TimeRange;
import com.akamai.wsa.contracts.AttackCategory;

public record AlertEvaluation(
        String ruleId,
        AttackCategory category,
        int threshold,
        int windowMinutes,
        long count,
        boolean firing,
        TimeRange window) {
}
