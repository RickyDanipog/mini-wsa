package com.akamai.wsa.analytics.infrastructure.persistence.postgres;

import com.akamai.wsa.analytics.domain.query.Interval;
import com.akamai.wsa.analytics.domain.query.TimeRange;
import com.akamai.wsa.analytics.domain.query.TimeSeriesBucket;
import com.akamai.wsa.analytics.domain.query.TimeSeriesQuery;
import com.akamai.wsa.analytics.domain.query.TimeSeriesResult;
import com.akamai.wsa.contracts.Action;
import com.akamai.wsa.contracts.AttackCategory;
import com.akamai.wsa.contracts.Severity;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;

import static org.assertj.core.api.Assertions.assertThat;

@Testcontainers
class PostgresAnalyticsReadStoreTimeSeriesIT {

    private static final int CONFIG_ID = 14227;
    private static final int OTHER_CONFIG_ID = 99001;
    private static final Instant BASE = Instant.parse("2026-07-06T09:00:00Z");

    private static final String CREATE_TABLE_STATEMENT = """
            CREATE TABLE IF NOT EXISTS events (
                event_id VARCHAR(128) PRIMARY KEY, timestamp TIMESTAMPTZ NOT NULL, config_id INTEGER NOT NULL,
                policy_id VARCHAR(128), client_ip VARCHAR(64) NOT NULL, hostname VARCHAR(255), path VARCHAR(2048),
                method VARCHAR(16), status_code INTEGER, user_agent TEXT, rule_id VARCHAR(128), rule_name VARCHAR(255),
                rule_message TEXT, severity VARCHAR(16), category VARCHAR(32), action VARCHAR(16) NOT NULL,
                geo_country VARCHAR(64), geo_city VARCHAR(128), request_size BIGINT, response_size BIGINT,
                attack_type VARCHAR(128), threat_score INTEGER NOT NULL, received_at TIMESTAMPTZ)
            """;

    private static final String INSERT_STATEMENT = """
            INSERT INTO events (
                event_id, timestamp, config_id, policy_id, client_ip, hostname, path, method, status_code,
                user_agent, rule_id, rule_name, rule_message, severity, category, action, geo_country, geo_city,
                request_size, response_size, attack_type, threat_score, received_at)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
            """;

    @Container
    static final PostgreSQLContainer<?> POSTGRES_CONTAINER = new PostgreSQLContainer<>("postgres:16-alpine");

    private PostgresAnalyticsReadStore postgresAnalyticsReadStore;

    @BeforeEach
    void setUp() {
        DriverManagerDataSource dataSource = new DriverManagerDataSource(
                POSTGRES_CONTAINER.getJdbcUrl(),
                POSTGRES_CONTAINER.getUsername(),
                POSTGRES_CONTAINER.getPassword());
        dataSource.setDriverClassName(POSTGRES_CONTAINER.getDriverClassName());
        JdbcTemplate jdbcTemplate = new JdbcTemplate(dataSource);
        jdbcTemplate.execute("DROP TABLE IF EXISTS events");
        jdbcTemplate.execute(CREATE_TABLE_STATEMENT);

        insert(jdbcTemplate, "evt-0001", 30, CONFIG_ID);
        insert(jdbcTemplate, "evt-0002", 120, CONFIG_ID);
        insert(jdbcTemplate, "evt-0003", 400, CONFIG_ID);
        insert(jdbcTemplate, "evt-0004", 250, OTHER_CONFIG_ID);

        postgresAnalyticsReadStore = new PostgresAnalyticsReadStore(jdbcTemplate);
    }

    @Test
    void bucketsStraddlingTwoFiveMinuteBucketsForConfig() {
        TimeSeriesResult result = postgresAnalyticsReadStore.timeSeries(
                new TimeSeriesQuery(CONFIG_ID, TimeRange.unbounded(), Interval.FIVE_MINUTES));

        assertThat(result.buckets()).containsExactly(
                new TimeSeriesBucket(BASE, 2L),
                new TimeSeriesBucket(BASE.plusSeconds(300), 1L));
    }

    @Test
    void isolatesByConfigId() {
        TimeSeriesResult result = postgresAnalyticsReadStore.timeSeries(
                new TimeSeriesQuery(OTHER_CONFIG_ID, TimeRange.unbounded(), Interval.FIVE_MINUTES));

        assertThat(result.buckets()).containsExactly(new TimeSeriesBucket(BASE, 1L));
    }

    private static void insert(JdbcTemplate jdbcTemplate, String eventId, long offsetSeconds, int configId) {
        Instant timestamp = BASE.plusSeconds(offsetSeconds);
        jdbcTemplate.update(INSERT_STATEMENT,
                eventId,
                toOffsetDateTime(timestamp),
                configId,
                "policy-" + configId,
                "203.0.113.42",
                "shop.example.com",
                "/api/v1/login",
                "POST",
                403,
                "Mozilla/5.0",
                "rule-INJECTION",
                "INJECTION signature",
                "INJECTION detected",
                Severity.HIGH.name(),
                AttackCategory.INJECTION.name(),
                Action.DENY.name(),
                "US",
                "New York",
                256L,
                512L,
                "INJECTION attack",
                80,
                toOffsetDateTime(timestamp.plusMillis(120)));
    }

    private static OffsetDateTime toOffsetDateTime(Instant instant) {
        return OffsetDateTime.ofInstant(instant, ZoneOffset.UTC);
    }
}
