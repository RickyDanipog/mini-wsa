package com.akamai.wsa;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;
import org.junit.jupiter.api.Test;

class ThreatScoringTest {
    @Test
    void scoresTheAssignmentMatrix() {
        var severityPoints = Map.of("CRITICAL", 40, "HIGH", 30, "MEDIUM", 20, "LOW", 10);
        var actionPoints = Map.of("DENY", 20, "ALERT", 10, "MONITOR", 0);
        for (var severity : SecurityEvent.Severity.values()) {
            for (var action : SecurityEvent.Action.values()) {
                int base = severityPoints.get(severity.name()) + actionPoints.get(action.name());
                assertThat(ThreatScoring.score(severity, action, "/", false)).isEqualTo(base);
                assertThat(ThreatScoring.score(severity, action, "/login", false)).isEqualTo(base + 15);
                assertThat(ThreatScoring.score(severity, action, "/admin", true)).isEqualTo(base + 30);
                assertThat(ThreatScoring.score(severity, action, "/admin/login", true)).isEqualTo(base + 30);
            }
        }
    }

    @Test
    void mapsEveryCategoryToItsAssignmentLabel() {
        assertThat(SecurityEvent.Category.values()).extracting(category -> category.attackType)
                .containsExactly("SQL/Command Injection", "Cross-Site Scripting", "Protocol Anomaly",
                        "Data Exfiltration", "Bot Activity", "Denial of Service", "Rate Limiting");
    }
}
