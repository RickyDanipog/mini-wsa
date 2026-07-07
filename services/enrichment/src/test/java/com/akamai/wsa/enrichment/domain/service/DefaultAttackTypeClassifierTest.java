package com.akamai.wsa.enrichment.domain.service;

import com.akamai.wsa.contracts.AttackCategory;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static org.assertj.core.api.Assertions.assertThat;

class DefaultAttackTypeClassifierTest {

    private final AttackTypeClassifier classifier = new DefaultAttackTypeClassifier();

    @Test
    void mapsCategoryToDisplayName() {
        assertThat(classifier.displayNameFor(AttackCategory.INJECTION)).isEqualTo("SQL/Command Injection");
        assertThat(classifier.displayNameFor(AttackCategory.RATE_LIMIT)).isEqualTo("Rate Limiting");
    }

    @ParameterizedTest
    @CsvSource({
            "INJECTION,SQL/Command Injection",
            "XSS,Cross-Site Scripting",
            "PROTOCOL_VIOLATION,Protocol Anomaly",
            "DATA_LEAKAGE,Data Exfiltration",
            "BOT,Bot Activity",
            "DOS,Denial of Service",
            "RATE_LIMIT,Rate Limiting"
    })
    void mapsEveryCategoryExhaustively(AttackCategory category, String expectedDisplayName) {
        assertThat(classifier.displayNameFor(category)).isEqualTo(expectedDisplayName);
    }
}
