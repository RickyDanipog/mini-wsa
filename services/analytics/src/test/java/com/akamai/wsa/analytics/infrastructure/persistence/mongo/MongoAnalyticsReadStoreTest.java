package com.akamai.wsa.analytics.infrastructure.persistence.mongo;

import com.akamai.wsa.analytics.domain.port.AnalyticsReadStore;
import com.akamai.wsa.analytics.domain.query.EventSamplesPage;
import com.akamai.wsa.analytics.domain.query.SampleQuery;
import com.akamai.wsa.analytics.domain.query.StatisticsQuery;
import com.akamai.wsa.analytics.domain.query.StatisticsSummary;
import com.akamai.wsa.analytics.domain.query.TimeRange;
import com.akamai.wsa.contracts.Action;
import com.akamai.wsa.contracts.AttackCategory;
import com.akamai.wsa.contracts.Severity;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.MongoDBContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

@Testcontainers
@SpringBootTest(properties = {"wsa.storage=mongo", "spring.autoconfigure.exclude="})
class MongoAnalyticsReadStoreTest {

    private static final int CONFIG_ID = 14227;
    private static final int OTHER_CONFIG_ID = 99999;

    @Container
    static final MongoDBContainer MONGO = new MongoDBContainer("mongo:7");

    @DynamicPropertySource
    static void mongoProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.data.mongodb.uri", () -> MONGO.getReplicaSetUrl("wsa"));
    }

    @Autowired
    AnalyticsReadStore readStore;

    @Autowired
    MongoTemplate mongoTemplate;

    @BeforeEach
    void seed() {
        mongoTemplate.getCollection("events").drop();
        mongoTemplate.insert(document("evt-1", CONFIG_ID, "203.0.113.42", "/api/v1/login",
                AttackCategory.INJECTION, Action.DENY, 80, Instant.parse("2026-05-20T14:00:00Z")));
        mongoTemplate.insert(document("evt-2", CONFIG_ID, "203.0.113.42", "/api/v1/login",
                AttackCategory.INJECTION, Action.DENY, 70, Instant.parse("2026-05-20T14:05:00Z")));
        mongoTemplate.insert(document("evt-3", CONFIG_ID, "198.51.100.7", "/admin",
                AttackCategory.BOT, Action.ALERT, 40, Instant.parse("2026-05-20T14:10:00Z")));
        mongoTemplate.insert(document("evt-9", OTHER_CONFIG_ID, "10.0.0.1", "/other",
                AttackCategory.XSS, Action.MONITOR, 55, Instant.parse("2026-05-20T14:20:00Z")));
    }

    @Test
    void usesTheMongoBackedReadStore() {
        assertThat(readStore).isInstanceOf(MongoAnalyticsReadStore.class);
    }

    @Test
    void summarizesCountsAveragesAndTopsForConfig() {
        StatisticsSummary summary = readStore.summarize(new StatisticsQuery(CONFIG_ID, TimeRange.unbounded()));

        assertThat(summary.totalEvents()).isEqualTo(3);
        assertThat(summary.byCategory().get(AttackCategory.INJECTION).count()).isEqualTo(2);
        assertThat(summary.byCategory().get(AttackCategory.INJECTION).averageThreatScore()).isEqualTo(75.0);
        assertThat(summary.byCategory().get(AttackCategory.BOT).count()).isEqualTo(1);
        assertThat(summary.byAction().get(Action.DENY)).isEqualTo(2);
        assertThat(summary.byAction().get(Action.ALERT)).isEqualTo(1);

        assertThat(summary.topAttackers().get(0).clientIp()).isEqualTo("203.0.113.42");
        assertThat(summary.topAttackers().get(0).count()).isEqualTo(2);
        assertThat(summary.topAttackers().get(0).averageThreatScore()).isEqualTo(75.0);

        assertThat(summary.topTargetedPaths().get(0).path()).isEqualTo("/api/v1/login");
        assertThat(summary.topTargetedPaths().get(0).count()).isEqualTo(2);
    }

    @Test
    void returnsSamplesNewestFirstWithTotalAndPaging() {
        EventSamplesPage firstPage = readStore.findSamples(
                new SampleQuery(CONFIG_ID, TimeRange.unbounded(), null, null, 2, 0));
        assertThat(firstPage.total()).isEqualTo(3);
        assertThat(firstPage.events()).hasSize(2);
        assertThat(firstPage.events().get(0).eventId()).isEqualTo("evt-3");
        assertThat(firstPage.events().get(1).eventId()).isEqualTo("evt-2");
        assertThat(firstPage.events().get(0).category()).isEqualTo(AttackCategory.BOT);
        assertThat(firstPage.events().get(0).attackType()).isEqualTo("BOT attack");

        EventSamplesPage secondPage = readStore.findSamples(
                new SampleQuery(CONFIG_ID, TimeRange.unbounded(), null, null, 2, 2));
        assertThat(secondPage.total()).isEqualTo(3);
        assertThat(secondPage.events()).hasSize(1);
        assertThat(secondPage.events().get(0).eventId()).isEqualTo("evt-1");
    }

    @Test
    void filtersSamplesByCategory() {
        EventSamplesPage bots = readStore.findSamples(
                new SampleQuery(CONFIG_ID, TimeRange.unbounded(), AttackCategory.BOT, null, 20, 0));
        assertThat(bots.total()).isEqualTo(1);
        assertThat(bots.events()).hasSize(1);
        assertThat(bots.events().get(0).eventId()).isEqualTo("evt-3");
    }

    private static EnrichedEventDocument document(String eventId, int configId, String clientIp, String path,
                                                  AttackCategory category, Action action, int threatScore,
                                                  Instant timestamp) {
        return new EnrichedEventDocument(
                eventId,
                eventId,
                timestamp,
                configId,
                "policy-" + configId,
                clientIp,
                "app.example.com",
                path,
                "POST",
                403,
                "curl/8.0",
                new EnrichedEventDocument.RuleDocument(
                        "rule-" + category.name(), category.name() + " rule",
                        category.name() + " detected", Severity.HIGH, category),
                action,
                new EnrichedEventDocument.GeoLocationDocument("US", "New York"),
                512L,
                1024L,
                category.name() + " attack",
                threatScore,
                timestamp.plusSeconds(1));
    }
}
