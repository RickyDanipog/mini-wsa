package com.akamai.wsa.analytics.infrastructure.seed;

import com.akamai.wsa.analytics.domain.model.EnrichedEventView;
import com.akamai.wsa.contracts.Action;
import com.akamai.wsa.contracts.AttackCategory;
import com.akamai.wsa.contracts.Severity;

import java.time.Instant;
import java.util.List;

public final class DevDataSeed {

    private static final String HOST = "shop.example.com";
    private static final Instant BASE = Instant.parse("2026-07-06T09:00:00Z");

    private DevDataSeed() {
    }

    public static List<EnrichedEventView> seedEvents() {
        return List.of(
                event("evt-0001", 60, 14227, "203.0.113.42", "/api/v1/login", "POST", 403,
                        Severity.CRITICAL, AttackCategory.INJECTION, Action.DENY, "SQL/Command Injection", 95),
                event("evt-0002", 120, 14227, "203.0.113.42", "/api/v1/login", "POST", 403,
                        Severity.CRITICAL, AttackCategory.INJECTION, Action.DENY, "SQL/Command Injection", 90),
                event("evt-0003", 180, 14227, "203.0.113.42", "/api/v1/login", "POST", 403,
                        Severity.HIGH, AttackCategory.INJECTION, Action.DENY, "SQL/Command Injection", 80),
                event("evt-0004", 240, 14227, "198.51.100.7", "/admin", "GET", 401,
                        Severity.HIGH, AttackCategory.BOT, Action.ALERT, "Bot Activity", 55),
                event("evt-0005", 300, 14227, "198.51.100.7", "/admin", "GET", 401,
                        Severity.MEDIUM, AttackCategory.BOT, Action.ALERT, "Bot Activity", 45),
                event("evt-0006", 360, 14227, "192.0.2.11", "/search", "GET", 200,
                        Severity.LOW, AttackCategory.XSS, Action.MONITOR, "Cross-Site Scripting", 25),
                event("evt-0007", 420, 14227, "192.0.2.11", "/search", "GET", 200,
                        Severity.MEDIUM, AttackCategory.XSS, Action.ALERT, "Cross-Site Scripting", 35),
                event("evt-0008", 480, 14227, "203.0.113.99", "/api/v1/orders", "POST", 429,
                        Severity.MEDIUM, AttackCategory.RATE_LIMIT, Action.DENY, "Rate Limiting", 40),
                event("evt-0009", 540, 99001, "198.51.100.7", "/api/v1/export", "GET", 403,
                        Severity.CRITICAL, AttackCategory.DATA_LEAKAGE, Action.DENY, "Data Exfiltration", 85),
                event("evt-0010", 600, 99001, "203.0.113.150", "/api/v1/export", "GET", 403,
                        Severity.HIGH, AttackCategory.DATA_LEAKAGE, Action.DENY, "Data Exfiltration", 75),
                event("evt-0011", 660, 99001, "203.0.113.150", "/", "GET", 503,
                        Severity.HIGH, AttackCategory.DOS, Action.ALERT, "Denial of Service", 60),
                event("evt-0012", 720, 99001, "203.0.113.150", "/", "GET", 503,
                        Severity.HIGH, AttackCategory.DOS, Action.ALERT, "Denial of Service", 60),
                event("evt-0013", 780, 99001, "192.0.2.200", "/api/v1/upload", "PUT", 400,
                        Severity.MEDIUM, AttackCategory.PROTOCOL_VIOLATION, Action.MONITOR, "Protocol Anomaly", 30),
                event("evt-0014", 840, 14227, "203.0.113.42", "/admin", "POST", 403,
                        Severity.CRITICAL, AttackCategory.INJECTION, Action.DENY, "SQL/Command Injection", 100),
                event("evt-0015", 900, 14227, "198.51.100.7", "/api/v1/login", "POST", 401,
                        Severity.MEDIUM, AttackCategory.BOT, Action.ALERT, "Bot Activity", 50));
    }

    private static EnrichedEventView event(String eventId, long offsetSeconds, int configId, String clientIp,
                                           String path, String method, int statusCode, Severity severity,
                                           AttackCategory category, Action action, String attackType, int threatScore) {
        Instant timestamp = BASE.plusSeconds(offsetSeconds);
        return new EnrichedEventView(
                eventId, timestamp, configId, "policy-" + configId, clientIp,
                HOST, path, method, statusCode, "Mozilla/5.0",
                "rule-" + category.name(), category.name() + " signature", attackType + " detected on " + path,
                severity, category, action, "US", "New York",
                256L, 512L, attackType, threatScore, timestamp.plusMillis(120));
    }
}
