# analytics Service Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development or superpowers:executing-plans. Steps use checkbox (`- [ ]`) syntax.

**Goal:** Build the read-side **analytics** service (`:8084`) — it serves the statistics summary and event-samples APIs by reading a **Mongo replica** of `event-store`'s enriched-event data. It consumes no Kafka and owns no write model.

**Architecture:** Hexagonal. A domain-owned `AnalyticsReadStore` port exposes `summarize(...)` and `findSamples(...)`; an **in-memory adapter** backs fast unit tests and a **Mongo adapter** (read-only, aggregation pipeline) backs integration + production. Controllers map the assignment's exact JSON shapes.

**Tech Stack:** Java 21, Spring Boot 3.3.5 (web, validation, actuator, data-mongodb), JUnit 5 + AssertJ, Testcontainers (MongoDB). Builds on the v2 restructure phase (module scaffold already present).

## Global Constraints

- Base package `com.akamai.wsa.analytics`. Shared enums come from `com.akamai.wsa.contracts` (`AttackCategory`, `Action`, `Severity`).
- **Read-only:** no writes, no Kafka. Mongo is connected read-only (reads the replica).
- `domain` imports no Spring/Mongo. The read port is domain-owned; adapters are `infrastructure`.
- Response shapes are the assignment's **exact** JSON — no `{success,data}` envelope. Pagination: `limit` default 20 / max 100 (clamped), `offset` ≥ 0, `total` always present.
- Records, immutability, intention-revealing names, FULL parameter names (no `v`/`e`). Conventional commits. Milestone tag `v0.5-analytics`.

---

### Task 1: Read model, read port, and in-memory adapter

**Files:**
- Create: `services/analytics/src/main/java/com/akamai/wsa/analytics/domain/model/EnrichedEventView.java`
- Create: `.../domain/query/{StatisticsQuery,StatisticsSummary,CategoryStatistics,AttackerStatistics,PathStatistics,SampleQuery,EventSamplesPage}.java`
- Create: `.../domain/port/AnalyticsReadStore.java`
- Create: `.../infrastructure/persistence/inmemory/InMemoryAnalyticsReadStore.java`
- Test: `.../test/java/com/akamai/wsa/analytics/infrastructure/persistence/inmemory/InMemoryAnalyticsReadStoreTest.java`
- Test support: `.../test/java/com/akamai/wsa/analytics/testsupport/EnrichedEventViews.java`

**Interfaces / Produces:**
- `EnrichedEventView` — the read projection of a stored enriched event (flat: eventId, timestamp, configId, policyId, clientIp, hostname, path, method, statusCode, userAgent, ruleId, ruleName, ruleMessage, severity, category, action, country, city, requestSize, responseSize, attackType, threatScore, receivedAt).
- `AnalyticsReadStore` with:
  - `StatisticsSummary summarize(StatisticsQuery statisticsQuery)`
  - `EventSamplesPage findSamples(SampleQuery sampleQuery)`

- [ ] **Step 1: Write the failing test**

```java
package com.akamai.wsa.analytics.infrastructure.persistence.inmemory;

import com.akamai.wsa.analytics.domain.query.*;
import com.akamai.wsa.contracts.AttackCategory;
import com.akamai.wsa.contracts.Action;
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
        assertThat(summary.topAttackers().get(0).count()).isEqualTo(2);
        assertThat(summary.topTargetedPaths().get(0).path()).isEqualTo("/api/v1/login");
    }

    @Test
    void returnsSamplesNewestFirstWithTotalAndPaging() {
        EventSamplesPage page = store.findSamples(new SampleQuery(14227, TimeRange.unbounded(), null, null, 2, 0));

        assertThat(page.total()).isEqualTo(3);
        assertThat(page.events()).hasSize(2);
        assertThat(page.events().get(0).eventId()).isEqualTo("evt-3"); // newest by timestamp
    }
}
```

- [ ] **Step 2: Run — expect FAIL.** `mvn -q -pl services/analytics test -Dtest=InMemoryAnalyticsReadStoreTest`

- [ ] **Step 3: Create the read model and query/result records**

`EnrichedEventView.java` (compact — the flat read projection):
```java
package com.akamai.wsa.analytics.domain.model;

import com.akamai.wsa.contracts.Action;
import com.akamai.wsa.contracts.AttackCategory;
import com.akamai.wsa.contracts.Severity;

import java.time.Instant;

public record EnrichedEventView(
        String eventId, Instant timestamp, int configId, String policyId, String clientIp,
        String hostname, String path, String method, int statusCode, String userAgent,
        String ruleId, String ruleName, String ruleMessage, Severity severity, AttackCategory category,
        Action action, String country, String city, long requestSize, long responseSize,
        String attackType, int threatScore, Instant receivedAt) {
}
```

`TimeRange.java`
```java
package com.akamai.wsa.analytics.domain.query;

import java.time.Instant;

public record TimeRange(Instant from, Instant to) {
    public static TimeRange unbounded() { return new TimeRange(null, null); }
    public boolean includes(Instant instant) {
        if (from != null && instant.isBefore(from)) return false;
        return to == null || !instant.isAfter(to);
    }
}
```

`StatisticsQuery.java`
```java
package com.akamai.wsa.analytics.domain.query;

public record StatisticsQuery(Integer configId, TimeRange timeRange) {
    public StatisticsQuery {
        if (timeRange == null) throw new IllegalArgumentException("timeRange must not be null");
    }
}
```

`CategoryStatistics.java`, `AttackerStatistics.java`, `PathStatistics.java`
```java
package com.akamai.wsa.analytics.domain.query;
public record CategoryStatistics(long count, double averageThreatScore) {}
```
```java
package com.akamai.wsa.analytics.domain.query;
public record AttackerStatistics(String clientIp, long count, double averageThreatScore) {}
```
```java
package com.akamai.wsa.analytics.domain.query;
public record PathStatistics(String path, long count) {}
```

`StatisticsSummary.java`
```java
package com.akamai.wsa.analytics.domain.query;

import com.akamai.wsa.contracts.Action;
import com.akamai.wsa.contracts.AttackCategory;

import java.util.List;
import java.util.Map;

public record StatisticsSummary(
        Integer configId, TimeRange timeRange, long totalEvents,
        Map<AttackCategory, CategoryStatistics> byCategory,
        Map<Action, Long> byAction,
        List<AttackerStatistics> topAttackers,
        List<PathStatistics> topTargetedPaths) {
    public static final int TOP_LIMIT = 10;
}
```

`SampleQuery.java`
```java
package com.akamai.wsa.analytics.domain.query;

import com.akamai.wsa.contracts.Action;
import com.akamai.wsa.contracts.AttackCategory;

public record SampleQuery(
        Integer configId, TimeRange timeRange, AttackCategory category, Action action, int limit, int offset) {
    public static final int DEFAULT_LIMIT = 20;
    public static final int MAXIMUM_LIMIT = 100;

    public SampleQuery {
        if (timeRange == null) throw new IllegalArgumentException("timeRange must not be null");
        if (limit < 1) throw new IllegalArgumentException("limit must be at least 1");
        if (offset < 0) throw new IllegalArgumentException("offset must not be negative");
    }

    public static int clampLimit(Integer requestedLimit) {
        if (requestedLimit == null) return DEFAULT_LIMIT;
        return Math.max(1, Math.min(MAXIMUM_LIMIT, requestedLimit));
    }
}
```

`EventSamplesPage.java`
```java
package com.akamai.wsa.analytics.domain.query;

import com.akamai.wsa.analytics.domain.model.EnrichedEventView;

import java.util.List;

public record EventSamplesPage(long total, int limit, int offset, List<EnrichedEventView> events) {}
```

- [ ] **Step 4: Create the read port**

```java
package com.akamai.wsa.analytics.domain.port;

import com.akamai.wsa.analytics.domain.query.EventSamplesPage;
import com.akamai.wsa.analytics.domain.query.SampleQuery;
import com.akamai.wsa.analytics.domain.query.StatisticsQuery;
import com.akamai.wsa.analytics.domain.query.StatisticsSummary;

public interface AnalyticsReadStore {
    StatisticsSummary summarize(StatisticsQuery statisticsQuery);
    EventSamplesPage findSamples(SampleQuery sampleQuery);
}
```

- [ ] **Step 5: Implement the in-memory adapter** (reference behavior for the Mongo adapter)

```java
package com.akamai.wsa.analytics.infrastructure.persistence.inmemory;

import com.akamai.wsa.analytics.domain.model.EnrichedEventView;
import com.akamai.wsa.analytics.domain.port.AnalyticsReadStore;
import com.akamai.wsa.analytics.domain.query.*;
import com.akamai.wsa.contracts.Action;
import com.akamai.wsa.contracts.AttackCategory;

import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

public class InMemoryAnalyticsReadStore implements AnalyticsReadStore {

    private final List<EnrichedEventView> events;

    public InMemoryAnalyticsReadStore(List<EnrichedEventView> events) {
        this.events = List.copyOf(events);
    }

    @Override
    public StatisticsSummary summarize(StatisticsQuery statisticsQuery) {
        List<EnrichedEventView> matched = filter(statisticsQuery.configId(), statisticsQuery.timeRange(), null, null);

        Map<AttackCategory, CategoryStatistics> byCategory = matched.stream()
                .collect(Collectors.groupingBy(EnrichedEventView::category))
                .entrySet().stream()
                .collect(Collectors.toMap(Map.Entry::getKey, entry -> new CategoryStatistics(
                        entry.getValue().size(),
                        entry.getValue().stream().mapToInt(EnrichedEventView::threatScore).average().orElse(0.0))));

        Map<Action, Long> byAction = matched.stream()
                .collect(Collectors.groupingBy(EnrichedEventView::action, Collectors.counting()));

        List<AttackerStatistics> topAttackers = matched.stream()
                .collect(Collectors.groupingBy(EnrichedEventView::clientIp))
                .entrySet().stream()
                .map(entry -> new AttackerStatistics(entry.getKey(), entry.getValue().size(),
                        entry.getValue().stream().mapToInt(EnrichedEventView::threatScore).average().orElse(0.0)))
                .sorted(Comparator.comparingLong(AttackerStatistics::count).reversed()
                        .thenComparing(AttackerStatistics::clientIp))
                .limit(StatisticsSummary.TOP_LIMIT).toList();

        List<PathStatistics> topTargetedPaths = matched.stream()
                .collect(Collectors.groupingBy(EnrichedEventView::path, Collectors.counting()))
                .entrySet().stream()
                .map(entry -> new PathStatistics(entry.getKey(), entry.getValue()))
                .sorted(Comparator.comparingLong(PathStatistics::count).reversed()
                        .thenComparing(PathStatistics::path))
                .limit(StatisticsSummary.TOP_LIMIT).toList();

        return new StatisticsSummary(statisticsQuery.configId(), statisticsQuery.timeRange(),
                matched.size(), byCategory, byAction, topAttackers, topTargetedPaths);
    }

    @Override
    public EventSamplesPage findSamples(SampleQuery sampleQuery) {
        List<EnrichedEventView> matched = filter(
                sampleQuery.configId(), sampleQuery.timeRange(), sampleQuery.category(), sampleQuery.action());
        List<EnrichedEventView> ordered = matched.stream()
                .sorted(Comparator.comparing(EnrichedEventView::timestamp).reversed())
                .toList();
        List<EnrichedEventView> page = ordered.stream()
                .skip(sampleQuery.offset()).limit(sampleQuery.limit()).toList();
        return new EventSamplesPage(ordered.size(), sampleQuery.limit(), sampleQuery.offset(), page);
    }

    private List<EnrichedEventView> filter(Integer configId, TimeRange timeRange, AttackCategory category, Action action) {
        return events.stream()
                .filter(event -> configId == null || event.configId() == configId)
                .filter(event -> timeRange.includes(event.timestamp()))
                .filter(event -> category == null || event.category() == category)
                .filter(event -> action == null || event.action() == action)
                .toList();
    }
}
```

Add `EnrichedEventViews.view(...)` test builder producing an `EnrichedEventView` with the given key fields and sensible defaults for the rest.

- [ ] **Step 6: Run — expect PASS.** **Step 7: Commit** — `feat(analytics): add read model, port, and in-memory read store`.

---

### Task 2: Statistics use case + controller

**Files:**
- Create: `.../application/SummarizeStatistics.java` (interface), `.../application/SummarizeStatisticsService.java`
- Create: `.../interfaces/rest/StatsController.java`, `.../interfaces/rest/StatisticsSummaryResponse.java`
- Test: `.../test/.../interfaces/rest/StatsControllerTest.java`

- [ ] **Step 1: Write `@WebMvcTest` StatsControllerTest** — mock `SummarizeStatistics`, GET `/v1/stats/summary?configId=14227&from=...&to=...`, assert JSON: `configId`, `timeRange.from/to`, `totalEvents`, `byCategory.INJECTION.count`/`.avgThreatScore`, `byAction.DENY`, `topAttackers[0].clientIp/count/avgThreatScore`, `topTargetedPaths[0].path/count`. Assert averages render to one decimal.
- [ ] **Step 2: Run — FAIL.**
- [ ] **Step 3:** `SummarizeStatistics` (interface: `StatisticsSummary summarize(StatisticsQuery)`); `SummarizeStatisticsService implements SummarizeStatistics` (a `@Service` delegating to `AnalyticsReadStore`).
- [ ] **Step 4:** `StatsController`: parse `configId` (nullable), ISO-8601 `from`/`to` (nullable → `TimeRange.unbounded()`), build `StatisticsQuery`, call the use case, map to `StatisticsSummaryResponse` (category-name string keys via `name()`, averages rounded to one decimal with a helper, `clientIp` already a string). Return 200.
- [ ] **Step 5: Run — PASS. Commit** — `feat(analytics): add statistics summary endpoint`.

---

### Task 3: Samples use case + controller + shared event response

**Files:**
- Create: `.../application/FetchEventSamples.java` (interface), `.../application/FetchEventSamplesService.java`
- Create: `.../interfaces/rest/SamplesController.java`, `.../interfaces/rest/SecurityEventResponse.java`, `.../interfaces/rest/EventSamplesResponse.java`
- Test: `.../test/.../interfaces/rest/SamplesControllerTest.java`

- [ ] **Step 1: Write `@WebMvcTest` SamplesControllerTest** — mock `FetchEventSamples`, GET `/v1/events/samples?configId=&category=&limit=2&offset=0`; assert `{total, limit, offset, results:[...]}`, `results` newest-first, and each result is the flat enriched-event shape (original DLR fields + `attackType` + `threatScore` + `receivedAt`). Assert invalid `category`/`action` enum → 400; `limit=500` clamps to 100.
- [ ] **Step 2: Run — FAIL.**
- [ ] **Step 3:** `FetchEventSamples` (interface: `EventSamplesPage fetch(SampleQuery)`); `FetchEventSamplesService implements FetchEventSamples` delegating to `AnalyticsReadStore`.
- [ ] **Step 4:** `SecurityEventResponse` maps one `EnrichedEventView` → flat assignment JSON (nested `rule{...}`, `geoLocation{...}` reconstructed for output). `EventSamplesResponse(total, limit, offset, results)`. `SamplesController`: all params optional, parse enums (invalid → 400 via advice), `clampLimit`, offset ≥ 0, build `SampleQuery`, call use case, map, return 200.
- [ ] **Step 5: Run — PASS. Commit** — `feat(analytics): add event samples endpoint`.

---

### Task 4: Mongo read adapter + Testcontainers integration

**Files:**
- Create: `.../infrastructure/persistence/mongo/EnrichedEventDocument.java` (`@Document("events")`, read mapping matching event-store's schema)
- Create: `.../infrastructure/persistence/mongo/MongoAnalyticsReadStore.java` (implements `AnalyticsReadStore` via `MongoTemplate` aggregation for summarize; find+sort+skip+limit+count for samples)
- Create: `.../infrastructure/config/ReadStoreConfiguration.java` (bean selection: Mongo adapter as primary; in-memory used only in tests)
- Test: `.../test/.../infrastructure/persistence/mongo/MongoAnalyticsReadStoreIT.java` (`@Testcontainers` MongoDB, seed documents, assert aggregation + paging match the in-memory reference)

- [ ] **Step 1: Write the Testcontainers IT** — start `MongoDBContainer`, insert enriched documents, assert `summarize`/`findSamples` return the same results the in-memory store does for the same data.
- [ ] **Step 2: Run — FAIL.**
- [ ] **Step 3:** Implement `EnrichedEventDocument` + `MongoAnalyticsReadStore` (aggregation pipeline: `$match` config/time/category/action → `$group` for category avg + action counts + attacker/path top-N via `$sortByCount`/`$limit`; samples via `Query` with `Sort.by(DESC,"timestamp")`, `.skip`/`.limit`, and a separate `count`). Read-only Mongo connection.
- [ ] **Step 4: Run — PASS.** **Step 5: Commit** — `feat(analytics): add MongoDB read adapter with aggregation`.

---

### Task 5 (optional bonus B3): Time-series endpoint

- [ ] Add `Interval` enum (`ONE_MINUTE`/`FIVE_MINUTES`/`ONE_HOUR`) with `floor(instant)` + `fromLabel("1m"|"5m"|"1h")`; a `BuildTimeSeries` use case bucketing matched events into `{bucketStart,count}` (TreeMap); `TimeSeriesController` for `GET /v1/stats/timeseries` (unknown interval → 400). Unit + `@WebMvcTest`. Commit — `feat(analytics): add time-series endpoint`. Keep OPTIONAL — implement only if time allows.

---

### Final: verify + tag

- [ ] `mvn -q -pl services/analytics verify` green. Dry-run on `:8084` against a seeded Mongo; `curl` both endpoints; confirm JSON shapes.
- [ ] Tag `v0.5-analytics`.

## Self-Review
- Covers stats summary + samples against the assignment's exact shapes, read-only from Mongo, with an in-memory reference adapter for fast tests and a Testcontainers-verified Mongo adapter.
- No placeholders; every step has concrete code or a precise instruction with the referenced types defined in Task 1.
- Reuses the aggregation/paging semantics specced in the Part 3 and Part 4 plans, re-homed to the read-only analytics service; enums sourced from `contracts`.
