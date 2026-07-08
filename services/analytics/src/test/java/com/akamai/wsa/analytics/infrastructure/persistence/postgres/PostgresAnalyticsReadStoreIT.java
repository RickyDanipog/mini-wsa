package com.akamai.wsa.analytics.infrastructure.persistence.postgres;

import com.akamai.wsa.analytics.domain.model.EnrichedEventView;
import com.akamai.wsa.analytics.domain.query.AttackerStatistics;
import com.akamai.wsa.analytics.domain.query.EventSamplesPage;
import com.akamai.wsa.analytics.domain.query.PathStatistics;
import com.akamai.wsa.analytics.domain.query.SampleQuery;
import com.akamai.wsa.analytics.domain.query.StatisticsQuery;
import com.akamai.wsa.analytics.domain.query.StatisticsSummary;
import com.akamai.wsa.analytics.domain.query.TimeRange;
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
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

@Testcontainers
class PostgresAnalyticsReadStoreIT {

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

        insert(jdbcTemplate, "evt-0001", 0, CONFIG_ID, "203.0.113.42", "/api/v1/login",
                AttackCategory.INJECTION, Action.DENY, 80);
        insert(jdbcTemplate, "evt-0002", 300, CONFIG_ID, "203.0.113.42", "/api/v1/login",
                AttackCategory.INJECTION, Action.DENY, 70);
        insert(jdbcTemplate, "evt-0003", 600, CONFIG_ID, "203.0.113.42", "/admin",
                AttackCategory.INJECTION, Action.DENY, 90);
        insert(jdbcTemplate, "evt-0004", 900, CONFIG_ID, "198.51.100.7", "/admin",
                AttackCategory.BOT, Action.ALERT, 40);
        insert(jdbcTemplate, "evt-0005", 1200, CONFIG_ID, "198.51.100.7", "/admin",
                AttackCategory.BOT, Action.ALERT, 50);
        insert(jdbcTemplate, "evt-0006", 1500, CONFIG_ID, "192.0.2.11", "/search",
                AttackCategory.XSS, Action.MONITOR, 30);
        insert(jdbcTemplate, "evt-0007", 1800, OTHER_CONFIG_ID, "203.0.113.150", "/api/v1/export",
                AttackCategory.DATA_LEAKAGE, Action.DENY, 85);

        postgresAnalyticsReadStore = new PostgresAnalyticsReadStore(jdbcTemplate);
    }

    @Test
    void summarizesTotalsAndCategoryAndActionForConfig() {
        StatisticsSummary summary = postgresAnalyticsReadStore.summarize(
                new StatisticsQuery(CONFIG_ID, TimeRange.unbounded()));

        assertThat(summary.totalEvents()).isEqualTo(6);

        assertThat(summary.byCategory().get(AttackCategory.INJECTION).count()).isEqualTo(3);
        assertThat(summary.byCategory().get(AttackCategory.INJECTION).averageThreatScore()).isEqualTo(80.0);
        assertThat(summary.byCategory().get(AttackCategory.BOT).count()).isEqualTo(2);
        assertThat(summary.byCategory().get(AttackCategory.BOT).averageThreatScore()).isEqualTo(45.0);
        assertThat(summary.byCategory().get(AttackCategory.XSS).count()).isEqualTo(1);
        assertThat(summary.byCategory().get(AttackCategory.XSS).averageThreatScore()).isEqualTo(30.0);

        assertThat(summary.byAction()).isEqualTo(Map.of(Action.DENY, 3L, Action.ALERT, 2L, Action.MONITOR, 1L));
    }

    @Test
    void summarizesTopAttackersOrderedByCountThenIp() {
        StatisticsSummary summary = postgresAnalyticsReadStore.summarize(
                new StatisticsQuery(CONFIG_ID, TimeRange.unbounded()));

        assertThat(summary.topAttackers())
                .extracting(AttackerStatistics::clientIp)
                .containsExactly("203.0.113.42", "198.51.100.7", "192.0.2.11");
        assertThat(summary.topAttackers())
                .extracting(AttackerStatistics::count)
                .containsExactly(3L, 2L, 1L);
        assertThat(summary.topAttackers().getFirst().averageThreatScore()).isEqualTo(80.0);
    }

    @Test
    void summarizesTopTargetedPathsOrderedByCountThenPath() {
        StatisticsSummary summary = postgresAnalyticsReadStore.summarize(
                new StatisticsQuery(CONFIG_ID, TimeRange.unbounded()));

        assertThat(summary.topTargetedPaths())
                .extracting(PathStatistics::path)
                .containsExactly("/admin", "/api/v1/login", "/search");
        assertThat(summary.topTargetedPaths())
                .extracting(PathStatistics::count)
                .containsExactly(3L, 2L, 1L);
    }

    @Test
    void summarizeIsolatesByConfigId() {
        StatisticsSummary summary = postgresAnalyticsReadStore.summarize(
                new StatisticsQuery(OTHER_CONFIG_ID, TimeRange.unbounded()));

        assertThat(summary.totalEvents()).isEqualTo(1);
        assertThat(summary.byCategory()).containsOnlyKeys(AttackCategory.DATA_LEAKAGE);
    }

    @Test
    void findSamplesOrdersByTimestampDescendingAndPages() {
        EventSamplesPage page = postgresAnalyticsReadStore.findSamples(
                new SampleQuery(CONFIG_ID, TimeRange.unbounded(), null, null, 2, 0));

        assertThat(page.total()).isEqualTo(6);
        assertThat(page.limit()).isEqualTo(2);
        assertThat(page.offset()).isZero();
        assertThat(page.events())
                .extracting(EnrichedEventView::eventId)
                .containsExactly("evt-0006", "evt-0005");
    }

    @Test
    void findSamplesFiltersByCategory() {
        EventSamplesPage page = postgresAnalyticsReadStore.findSamples(
                new SampleQuery(CONFIG_ID, TimeRange.unbounded(), AttackCategory.INJECTION, null, 10, 0));

        assertThat(page.total()).isEqualTo(3);
        assertThat(page.events())
                .extracting(EnrichedEventView::eventId)
                .containsExactly("evt-0003", "evt-0002", "evt-0001");
    }

    @Test
    void findSamplesFiltersByActionAndConfig() {
        EventSamplesPage page = postgresAnalyticsReadStore.findSamples(
                new SampleQuery(CONFIG_ID, TimeRange.unbounded(), null, Action.ALERT, 10, 0));

        assertThat(page.total()).isEqualTo(2);
        assertThat(page.events())
                .extracting(EnrichedEventView::eventId)
                .containsExactly("evt-0005", "evt-0004");
        assertThat(page.events()).allSatisfy(event -> assertThat(event.action()).isEqualTo(Action.ALERT));
    }

    @Test
    void findSamplesMapsSharedContractShape() {
        EventSamplesPage page = postgresAnalyticsReadStore.findSamples(
                new SampleQuery(CONFIG_ID, TimeRange.unbounded(), AttackCategory.XSS, null, 10, 0));

        EnrichedEventView event = page.events().getFirst();
        assertThat(event.eventId()).isEqualTo("evt-0006");
        assertThat(event.timestamp()).isEqualTo(BASE.plusSeconds(1500));
        assertThat(event.configId()).isEqualTo(CONFIG_ID);
        assertThat(event.clientIp()).isEqualTo("192.0.2.11");
        assertThat(event.path()).isEqualTo("/search");
        assertThat(event.severity()).isEqualTo(Severity.MEDIUM);
        assertThat(event.category()).isEqualTo(AttackCategory.XSS);
        assertThat(event.action()).isEqualTo(Action.MONITOR);
        assertThat(event.country()).isEqualTo("US");
        assertThat(event.threatScore()).isEqualTo(30);
        assertThat(event.receivedAt()).isEqualTo(BASE.plusSeconds(1500).plusMillis(120));
    }

    @Test
    void countsByCategoryWithinWindowRespectingInclusiveBoundariesAcrossConfigs() {
        assertThat(postgresAnalyticsReadStore.countByCategoryWithin(AttackCategory.INJECTION,
                new TimeRange(BASE, BASE.plusSeconds(600))))
                .isEqualTo(3);

        assertThat(postgresAnalyticsReadStore.countByCategoryWithin(AttackCategory.INJECTION,
                new TimeRange(BASE.plusSeconds(1), BASE.plusSeconds(600))))
                .isEqualTo(2);

        assertThat(postgresAnalyticsReadStore.countByCategoryWithin(AttackCategory.BOT,
                new TimeRange(BASE, BASE.plusSeconds(2000))))
                .isEqualTo(2);

        assertThat(postgresAnalyticsReadStore.countByCategoryWithin(AttackCategory.DATA_LEAKAGE,
                new TimeRange(BASE, BASE.plusSeconds(2000))))
                .isEqualTo(1);

        assertThat(postgresAnalyticsReadStore.countByCategoryWithin(AttackCategory.DOS,
                new TimeRange(BASE, BASE.plusSeconds(2000))))
                .isZero();
    }

    private static void insert(JdbcTemplate jdbcTemplate, String eventId, long offsetSeconds, int configId,
                               String clientIp, String path, AttackCategory category, Action action, int threatScore) {
        Instant timestamp = BASE.plusSeconds(offsetSeconds);
        jdbcTemplate.update(INSERT_STATEMENT,
                eventId,
                toOffsetDateTime(timestamp),
                configId,
                "policy-" + configId,
                clientIp,
                "shop.example.com",
                path,
                "POST",
                403,
                "Mozilla/5.0",
                "rule-" + category.name(),
                category.name() + " signature",
                category.name() + " detected on " + path,
                Severity.MEDIUM.name(),
                category.name(),
                action.name(),
                "US",
                "New York",
                256L,
                512L,
                category.name() + " attack",
                threatScore,
                toOffsetDateTime(timestamp.plusMillis(120)));
    }

    private static OffsetDateTime toOffsetDateTime(Instant instant) {
        return OffsetDateTime.ofInstant(instant, ZoneOffset.UTC);
    }
}
