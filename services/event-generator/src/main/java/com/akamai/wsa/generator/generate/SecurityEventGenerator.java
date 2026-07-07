package com.akamai.wsa.generator.generate;

import com.akamai.wsa.generator.model.GeneratedEvent;
import com.akamai.wsa.generator.model.GeneratedGeoLocation;
import com.akamai.wsa.generator.model.GeneratedRule;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Random;

public class SecurityEventGenerator {

    private static final List<String> CATEGORIES =
            List.of("INJECTION", "XSS", "PROTOCOL_VIOLATION", "DATA_LEAKAGE", "BOT", "DOS", "RATE_LIMIT");
    private static final List<String> SEVERITIES = List.of("CRITICAL", "HIGH", "MEDIUM", "LOW");
    private static final List<String> ACTIONS = List.of("DENY", "ALERT", "MONITOR");
    private static final List<String> METHODS = List.of("GET", "POST", "PUT", "DELETE");
    private static final List<String> PATHS = List.of(
            "/login", "/admin", "/admin/users", "/api/v1/login", "/api/v1/users",
            "/checkout", "/api/v1/search", "/wp-admin", "/api/v1/orders",
            "/static/app.js", "/api/v1/payments");
    private static final List<String> HOSTNAMES =
            List.of("www.example.com", "api.example.com", "shop.example.com");
    private static final List<GeneratedGeoLocation> GEO_LOCATIONS = List.of(
            new GeneratedGeoLocation("CN", "Beijing"),
            new GeneratedGeoLocation("RU", "Moscow"),
            new GeneratedGeoLocation("US", "Ashburn"),
            new GeneratedGeoLocation("BR", "Sao Paulo"),
            new GeneratedGeoLocation("IN", "Mumbai"),
            new GeneratedGeoLocation("DE", "Frankfurt"));
    private static final List<Integer> CONFIG_IDS = List.of(14227, 20351, 88120);
    private static final List<String> USER_AGENTS = List.of(
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36",
            "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/605.1.15",
            "curl/8.4.0",
            "python-requests/2.31.0",
            "sqlmap/1.7");

    private final Instant baseTimestamp;

    public SecurityEventGenerator(Instant baseTimestamp) {
        this.baseTimestamp = baseTimestamp;
    }

    public GeneratedEvent generateNormalEvent(int sequenceNumber, Random random) {
        String clientIp = randomIp(random);
        String path = PATHS.get(random.nextInt(PATHS.size()));
        Instant timestamp = baseTimestamp.plus(sequenceNumber, ChronoUnit.SECONDS);
        return buildEvent(sequenceNumber, clientIp, path, timestamp, random);
    }

    public GeneratedEvent generateWaveEvent(int sequenceNumber, String clientIp, String path,
                                            Instant timestamp, Random random) {
        return buildEvent(sequenceNumber, clientIp, path, timestamp, random);
    }

    public String randomIp(Random random) {
        return (1 + random.nextInt(223)) + "." + random.nextInt(256) + "."
                + random.nextInt(256) + "." + (1 + random.nextInt(254));
    }

    private GeneratedEvent buildEvent(int sequenceNumber, String clientIp, String path,
                                      Instant timestamp, Random random) {
        String category = CATEGORIES.get(random.nextInt(CATEGORIES.size()));
        String severity = SEVERITIES.get(random.nextInt(SEVERITIES.size()));
        String action = ACTIONS.get(random.nextInt(ACTIONS.size()));
        int configId = CONFIG_IDS.get(random.nextInt(CONFIG_IDS.size()));
        int statusCode = "DENY".equals(action) ? 403 : (random.nextBoolean() ? 200 : 401);
        return new GeneratedEvent(
                "evt-" + String.format("%08d", sequenceNumber),
                timestamp,
                configId,
                "pol_web" + (1 + random.nextInt(3)),
                clientIp,
                HOSTNAMES.get(random.nextInt(HOSTNAMES.size())),
                path,
                METHODS.get(random.nextInt(METHODS.size())),
                statusCode,
                USER_AGENTS.get(random.nextInt(USER_AGENTS.size())),
                new GeneratedRule(
                        "9500" + String.format("%02d", random.nextInt(100)),
                        category + "_RULE",
                        category + " detected",
                        severity,
                        category),
                action,
                GEO_LOCATIONS.get(random.nextInt(GEO_LOCATIONS.size())),
                64L + random.nextInt(4096),
                64L + random.nextInt(2048));
    }
}
