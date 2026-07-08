package com.akamai.wsa.analytics.domain.alert;

import com.akamai.wsa.contracts.AttackCategory;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AlertRuleTest {

    @Test
    void acceptsValidRule() {
        AlertRule rule = new AlertRule("rule-1", AttackCategory.INJECTION, 3, 10);

        assertThat(rule.id()).isEqualTo("rule-1");
        assertThat(rule.category()).isEqualTo(AttackCategory.INJECTION);
        assertThat(rule.threshold()).isEqualTo(3);
        assertThat(rule.windowMinutes()).isEqualTo(10);
    }

    @Test
    void rejectsBlankId() {
        assertThatThrownBy(() -> new AlertRule(" ", AttackCategory.INJECTION, 1, 1))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("alert rule id must not be blank");
    }

    @Test
    void rejectsNullCategory() {
        assertThatThrownBy(() -> new AlertRule("rule-1", null, 1, 1))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("alert rule category must not be null");
    }

    @Test
    void rejectsThresholdBelowOne() {
        assertThatThrownBy(() -> new AlertRule("rule-1", AttackCategory.INJECTION, 0, 1))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("alert rule threshold must be at least 1");
    }

    @Test
    void rejectsWindowMinutesBelowOne() {
        assertThatThrownBy(() -> new AlertRule("rule-1", AttackCategory.INJECTION, 1, 0))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("alert rule windowMinutes must be at least 1");
    }
}
