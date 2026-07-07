package com.akamai.wsa.analytics.infrastructure.persistence.inmemory;

import com.akamai.wsa.analytics.domain.query.EventSamplesPage;
import com.akamai.wsa.analytics.domain.query.SampleQuery;
import com.akamai.wsa.analytics.domain.query.StatisticsQuery;
import com.akamai.wsa.analytics.domain.query.StatisticsSummary;
import com.akamai.wsa.analytics.domain.query.TimeRange;
import com.akamai.wsa.contracts.Action;
import com.akamai.wsa.contracts.AttackCategory;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;

import static com.akamai.wsa.analytics.testsupport.EnrichedEventViews.view;
import static org.assertj.core.api.Assertions.assertThat;

class InMemoryAnalyticsReadStoreTest {

    private final InMemoryAnalyticsReadStore store = new InMemoryAnalyticsReadStore(List.of(
            view("evt-1", 14227, "203.0.113.42", "/api/v1/login", AttackCategory.INJECTION, Action.DENY, 80,
                    Instant.parse("2026-05-20T14:00:00Z")),
            view("evt-2", 14227, "203.0.113.42", "/api/v1/login", AttackCategory.INJECTION, Action.DENY, 70,
                    Instant.parse("2026-05-20T14:05:00Z")),
            view("evt-3", 14227, "198.51.100.7", "/admin", AttackCategory.BOT, Action.ALERT, 40,
                    Instant.parse("2026-05-20T14:10:00Z"))));

    @Test
    void summarizesCountsAveragesAndTops() {
        StatisticsSummary summary = store.summarize(new StatisticsQuery(14227, TimeRange.unbounded()));
        assertThat(summary.totalEvents()).isEqualTo(3);
        assertThat(summary.byCategory().get(AttackCategory.INJECTION).count()).isEqualTo(2);
        assertThat(summary.byCategory().get(AttackCategory.INJECTION).averageThreatScore()).isEqualTo(75.0);
        assertThat(summary.byAction().get(Action.DENY)).isEqualTo(2);
        assertThat(summary.topAttackers().get(0).clientIp()).isEqualTo("203.0.113.42");
        assertThat(summary.topTargetedPaths().get(0).path()).isEqualTo("/api/v1/login");
    }

    @Test
    void returnsSamplesNewestFirstWithTotalAndPaging() {
        EventSamplesPage page = store.findSamples(new SampleQuery(14227, TimeRange.unbounded(), null, null, 2, 0));
        assertThat(page.total()).isEqualTo(3);
        assertThat(page.events()).hasSize(2);
        assertThat(page.events().get(0).eventId()).isEqualTo("evt-3");
    }

    @Test
    void appliesTimeRangeConfigCategoryAndActionFilters() {
        StatisticsSummary bounded = store.summarize(new StatisticsQuery(14227,
                new TimeRange(Instant.parse("2026-05-20T14:04:00Z"), Instant.parse("2026-05-20T14:06:00Z"))));
        assertThat(bounded.totalEvents()).isEqualTo(1);

        EventSamplesPage botOnly = store.findSamples(
                new SampleQuery(null, TimeRange.unbounded(), AttackCategory.BOT, null, 20, 0));
        assertThat(botOnly.total()).isEqualTo(1);
        assertThat(botOnly.events().get(0).eventId()).isEqualTo("evt-3");

        EventSamplesPage denyOnly = store.findSamples(
                new SampleQuery(null, TimeRange.unbounded(), null, Action.DENY, 20, 0));
        assertThat(denyOnly.total()).isEqualTo(2);
    }

    @Test
    void pagingOffsetSkipsNewestEvents() {
        EventSamplesPage secondPage = store.findSamples(new SampleQuery(14227, TimeRange.unbounded(), null, null, 2, 2));
        assertThat(secondPage.total()).isEqualTo(3);
        assertThat(secondPage.events()).hasSize(1);
        assertThat(secondPage.events().get(0).eventId()).isEqualTo("evt-1");
    }
}
