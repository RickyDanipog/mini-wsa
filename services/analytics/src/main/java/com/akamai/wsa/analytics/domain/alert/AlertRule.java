package com.akamai.wsa.analytics.domain.alert;

import com.akamai.wsa.contracts.AttackCategory;

public record AlertRule(String id, AttackCategory category, int threshold, int windowMinutes) {
    public AlertRule {
        if (id == null || id.isBlank()) {
            throw new IllegalArgumentException("alert rule id must not be blank");
        }
        if (category == null) {
            throw new IllegalArgumentException("alert rule category must not be null");
        }
        if (threshold < 1) {
            throw new IllegalArgumentException("alert rule threshold must be at least 1");
        }
        if (windowMinutes < 1) {
            throw new IllegalArgumentException("alert rule windowMinutes must be at least 1");
        }
    }
}
