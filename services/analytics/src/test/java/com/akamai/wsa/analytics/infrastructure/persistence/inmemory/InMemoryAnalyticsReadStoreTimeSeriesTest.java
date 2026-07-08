package com.akamai.wsa.analytics.infrastructure.persistence.inmemory;

import com.akamai.wsa.analytics.domain.query.Interval;
import com.akamai.wsa.analytics.domain.query.TimeRange;
import com.akamai.wsa.analytics.domain.query.TimeSeriesBucket;
import com.akamai.wsa.analytics.domain.query.TimeSeriesQuery;
import com.akamai.wsa.analytics.domain.query.TimeSeriesResult;
import com.akamai.wsa.contracts.Action;
import com.akamai.wsa.contracts.AttackCategory;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;

import static com.akamai.wsa.analytics.testsupport.EnrichedEventViews.view;
import static org.assertj.core.api.Assertions.assertThat;

class InMemoryAnalyticsReadStoreTimeSeriesTest {

    private final InMemoryAnalyticsReadStore store = new InMemoryAnalyticsReadStore(List.of(
            view("evt-1", 14227, "203.0.113.42", "/api/v1/login", AttackCategory.INJECTION, Action.DENY, 80,
                    Instant.parse("2026-05-20T14:00:30Z")),
            view("evt-2", 14227, "203.0.113.42", "/api/v1/login", AttackCategory.INJECTION, Action.DENY, 70,
                    Instant.parse("2026-05-20T14:02:00Z")),
            view("evt-3", 14227, "198.51.100.7", "/admin", AttackCategory.BOT, Action.ALERT, 40,
                    Instant.parse("2026-05-20T14:07:00Z")),
            view("evt-4", 99001, "192.0.2.11", "/search", AttackCategory.XSS, Action.MONITOR, 30,
                    Instant.parse("2026-05-20T14:08:00Z")),
            view("evt-5", 14227, "203.0.113.42", "/admin", AttackCategory.INJECTION, Action.DENY, 90,
                    Instant.parse("2026-05-20T14:13:00Z"))));

    @Test
    void bucketsByFiveMinutesForConfigSortedAscendingSparse() {
        TimeSeriesResult result = store.timeSeries(new TimeSeriesQuery(
                14227, TimeRange.unbounded(), Interval.FIVE_MINUTES));

        assertThat(result.buckets()).containsExactly(
                new TimeSeriesBucket(Instant.parse("2026-05-20T14:00:00Z"), 2L),
                new TimeSeriesBucket(Instant.parse("2026-05-20T14:05:00Z"), 1L),
                new TimeSeriesBucket(Instant.parse("2026-05-20T14:10:00Z"), 1L));
    }

    @Test
    void bucketsByOneMinuteFlooredToBoundary() {
        TimeSeriesResult result = store.timeSeries(new TimeSeriesQuery(
                14227, TimeRange.unbounded(), Interval.ONE_MINUTE));

        assertThat(result.buckets()).containsExactly(
                new TimeSeriesBucket(Instant.parse("2026-05-20T14:00:00Z"), 1L),
                new TimeSeriesBucket(Instant.parse("2026-05-20T14:02:00Z"), 1L),
                new TimeSeriesBucket(Instant.parse("2026-05-20T14:07:00Z"), 1L),
                new TimeSeriesBucket(Instant.parse("2026-05-20T14:13:00Z"), 1L));
    }

    @Test
    void appliesConfigAndTimeRangeFilter() {
        TimeSeriesResult otherConfig = store.timeSeries(new TimeSeriesQuery(
                99001, TimeRange.unbounded(), Interval.FIVE_MINUTES));
        assertThat(otherConfig.buckets()).containsExactly(
                new TimeSeriesBucket(Instant.parse("2026-05-20T14:05:00Z"), 1L));

        TimeSeriesResult bounded = store.timeSeries(new TimeSeriesQuery(
                14227,
                new TimeRange(Instant.parse("2026-05-20T14:01:00Z"), Instant.parse("2026-05-20T14:08:00Z")),
                Interval.FIVE_MINUTES));
        assertThat(bounded.buckets()).containsExactly(
                new TimeSeriesBucket(Instant.parse("2026-05-20T14:00:00Z"), 1L),
                new TimeSeriesBucket(Instant.parse("2026-05-20T14:05:00Z"), 1L));
    }
}
