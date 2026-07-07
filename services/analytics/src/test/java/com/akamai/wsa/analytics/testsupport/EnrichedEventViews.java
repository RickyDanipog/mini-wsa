package com.akamai.wsa.analytics.testsupport;

import com.akamai.wsa.analytics.domain.model.EnrichedEventView;
import com.akamai.wsa.contracts.Action;
import com.akamai.wsa.contracts.AttackCategory;
import com.akamai.wsa.contracts.Severity;

import java.time.Instant;

public final class EnrichedEventViews {

    private EnrichedEventViews() {
    }

    public static EnrichedEventView view(String eventId, int configId, String clientIp, String path,
                                         AttackCategory category, Action action, int threatScore, Instant timestamp) {
        return new EnrichedEventView(
                eventId, timestamp, configId, "policy-" + configId, clientIp,
                "app.example.com", path, "POST", 403, "curl/8.0",
                "rule-" + category.name(), category.name() + " rule", category.name() + " detected",
                Severity.HIGH, category, action, "US", "New York",
                512L, 1024L, category.name() + " attack", threatScore, timestamp.plusSeconds(1));
    }
}
