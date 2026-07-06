package com.akamai.wsa.domain.model;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class AttackTypeTest {

    @Test
    void mapsEveryCategoryToTheAssignmentDisplayName() {
        assertThat(AttackType.fromCategory(AttackCategory.INJECTION).displayName())
                .isEqualTo("SQL/Command Injection");
        assertThat(AttackType.fromCategory(AttackCategory.XSS).displayName())
                .isEqualTo("Cross-Site Scripting");
        assertThat(AttackType.fromCategory(AttackCategory.PROTOCOL_VIOLATION).displayName())
                .isEqualTo("Protocol Anomaly");
        assertThat(AttackType.fromCategory(AttackCategory.DATA_LEAKAGE).displayName())
                .isEqualTo("Data Exfiltration");
        assertThat(AttackType.fromCategory(AttackCategory.BOT).displayName())
                .isEqualTo("Bot Activity");
        assertThat(AttackType.fromCategory(AttackCategory.DOS).displayName())
                .isEqualTo("Denial of Service");
        assertThat(AttackType.fromCategory(AttackCategory.RATE_LIMIT).displayName())
                .isEqualTo("Rate Limiting");
    }
}
