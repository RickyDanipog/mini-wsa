# analytics Service Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development or superpowers:executing-plans. Steps use checkbox (`- [ ]`) syntax.
>
> **Patched 2026-07-07** from `reconciliation.md`: the read store is now **in-memory-first** — the in-memory adapter is the DEFAULT runnable bean (with a dev seed) so `:8084` can be dry-run with no Mongo; the Mongo adapter moves behind a `mongo` profile and its Testcontainers task is **deferred to the DB-candidate phase**. Read `.claude/context.md` and `services/contracts/.claude/context.md` first.

**Goal:** Build the read-side **analytics** service (`:8084`) — serves the statistics summary and event-samples APIs. Data source is a **Mongo replica** of `event-store`'s enriched-event data; for dry runs/sanity it runs on a **seeded in-memory** read store. Consumes no Kafka, owns no write model.

**Architecture:** Hexagonal. A domain-owned `AnalyticsReadStore` port exposes `summarize(...)` and `findSamples(...)`. The **in-memory adapter is the default runnable bean** (`@Profile("!mongo")`, dev-seeded); a **Mongo adapter** (`@Profile("mongo")`, read-only aggregation) is added in the later DB phase. Controllers map the assignment's exact JSON shapes.

**Tech Stack:** Java 21, Spring Boot 3.3.5 (web, validation, actuator; data-mongodb only when the `mongo` profile lands), JUnit 5 + AssertJ. Builds on the v2 restructure scaffold.

## Global Constraints

- Base package `com.akamai.wsa.analytics`. Shared enums come from `com.akamai.wsa.contracts` (`AttackCategory`, `Action`, `Severity`) — see `services/contracts/.claude/context.md`.
- **Read-only:** no writes, no Kafka.
- **In-memory-first:** the runnable default (`!mongo` profile) uses the seeded in-memory read store — no external store required to dry-run. Mongo is opt-in (`mongo` profile), deferred to the DB phase.
- `domain` imports no Spring/Mongo. The read port is domain-owned; adapters are `infrastructure`.
- Response shapes are the assignment's **exact** JSON — no `{success,data}` envelope. Pagination: `limit` default 20 / max 100 (clamped), `offset` ≥ 0, `total` always present.
- Records, immutability, intention-revealing names, FULL parameter names (no `v`/`e`). Conventional commits. Milestone tag `v0.5-analytics`.

---

### Task 1: Read model, read port, and in-memory adapter

*(Unchanged from the reconciled plan — pure, no infra; this is correct and first.)*

**Files:**
- Create: `.../domain/model/EnrichedEventView.java`
- Create: `.../domain/query/{TimeRange,StatisticsQuery,StatisticsSummary,CategoryStatistics,AttackerStatistics,PathStatistics,SampleQuery,EventSamplesPage}.java`
- Create: `.../domain/port/AnalyticsReadStore.java`
- Create: `.../infrastructure/persistence/inmemory/InMemoryAnalyticsReadStore.java`
- Test: `.../test/.../inmemory/InMemoryAnalyticsReadStoreTest.java`
- Test support: `.../test/.../testsupport/EnrichedEventViews.java`

**Produces:**
- `EnrichedEventView` — flat read projection (eventId, timestamp, configId, policyId, clientIp, hostname, path, method, statusCode, userAgent, ruleId, ruleName, ruleMessage, severity, category, action, country, city, requestSize, responseSize, attackType, threatScore, receivedAt).
- `AnalyticsReadStore { StatisticsSummary summarize(StatisticsQuery); EventSamplesPage findSamples(SampleQuery); }`

- [ ] **Step 1: Failing test** (`InMemoryAnalyticsReadStoreTest`)

```java
package com.akamai.wsa.analytics.infrastructure.persistence.inmemory;

import com.akamai.wsa.analytics.domain.query.*;
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
}
```

- [ ] **Step 2: Run — FAIL.** `mvn -q -pl services/analytics test -Dtest=InMemoryAnalyticsReadStoreTest`
- [ ] **Step 3–5:** Create the records, the port, and `InMemoryAnalyticsReadStore` exactly as specified below, plus an `EnrichedEventViews.view(...)` test builder.

`EnrichedEventView.java`
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

`CategoryStatistics.java` / `AttackerStatistics.java` / `PathStatistics.java`
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

`AnalyticsReadStore.java`
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

`InMemoryAnalyticsReadStore.java`
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

- [ ] **Step 6: Run — PASS. Commit** — `feat(analytics): add read model, port, and in-memory read store`.

---

### Task 2: Statistics use case + controller

*(Unchanged — achievable now against the in-memory store, zero DB.)*

- [ ] **Step 1:** `@WebMvcTest StatsControllerTest` — mock `SummarizeStatistics`, `GET /v1/stats/summary?configId=14227&from=&to=`, assert JSON: `configId`, `timeRange.from/to`, `totalEvents`, `byCategory.INJECTION.count`/`.avgThreatScore`, `byAction.DENY`, `topAttackers[0].clientIp/count/avgThreatScore`, `topTargetedPaths[0].path/count`; averages to one decimal. — Run FAIL.
- [ ] **Step 2:** `SummarizeStatistics` (interface `StatisticsSummary summarize(StatisticsQuery)`); `SummarizeStatisticsService implements SummarizeStatistics` (`@Service`, delegates to `AnalyticsReadStore`).
- [ ] **Step 3:** `StatsController`: parse nullable `configId`, ISO-8601 `from`/`to` (null → `TimeRange.unbounded()`), build query, call use case, map to `StatisticsSummaryResponse` (category-name string keys via `name()`, averages rounded to one decimal, `clientIp` already string). 200. — Run PASS. Commit `feat(analytics): add statistics summary endpoint`.

---

### Task 3: Samples use case + controller + shared event response

*(Unchanged — achievable now.)*

- [ ] **Step 1:** `@WebMvcTest SamplesControllerTest` — mock `FetchEventSamples`, `GET /v1/events/samples?configId=&category=&limit=2&offset=0`; assert `{total,limit,offset,results:[...]}`, newest-first, each result the flat enriched-event shape (DLR fields + `attackType` + `threatScore` + `receivedAt`); invalid enum → 400; `limit=500` clamps to 100. — Run FAIL.
- [ ] **Step 2:** `FetchEventSamples` (interface `EventSamplesPage fetch(SampleQuery)`); `FetchEventSamplesService` (`@Service`) delegating to `AnalyticsReadStore`.
- [ ] **Step 3:** `SecurityEventResponse` maps one `EnrichedEventView` → flat assignment JSON (nested `rule{...}`/`geoLocation{...}` reconstructed); `EventSamplesResponse(total,limit,offset,results)`; `SamplesController` all-optional params, enum-parse (invalid → 400 via `@RestControllerAdvice`), `clampLimit`, offset ≥ 0. 200. — Run PASS. Commit `feat(analytics): add event samples endpoint`.

---

### Task 4: Make in-memory the default runnable bean + dev seed  ⟵ NEW (in-memory-first)

**Files:**
- Create: `.../infrastructure/config/ReadStoreConfiguration.java`
- Create: `.../infrastructure/seed/DevDataSeed.java` (a small fixture list of `EnrichedEventView`s)
- Test: `.../test/.../ReadStoreConfigurationTest.java` (context loads with default profile; the in-memory store bean is present and seeded)

- [ ] **Step 1:** Write a `@SpringBootTest` (default profile) asserting an `AnalyticsReadStore` bean exists and returns the seeded data (e.g. `summarize` totalEvents > 0). — Run FAIL.
- [ ] **Step 2:** `DevDataSeed` — a static method returning ~10–20 realistic `EnrichedEventView`s (varied configId/category/action/clientIp/path/timestamp) so both endpoints return meaningful data with no external store.
- [ ] **Step 3:** `ReadStoreConfiguration`:
```java
package com.akamai.wsa.analytics.infrastructure.config;

import com.akamai.wsa.analytics.domain.port.AnalyticsReadStore;
import com.akamai.wsa.analytics.infrastructure.persistence.inmemory.InMemoryAnalyticsReadStore;
import com.akamai.wsa.analytics.infrastructure.seed.DevDataSeed;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;

@Configuration
public class ReadStoreConfiguration {

    // Default runnable store for dry runs / sanity — active unless the `mongo` profile is on.
    @Bean
    @Profile("!mongo")
    public AnalyticsReadStore inMemoryAnalyticsReadStore() {
        return new InMemoryAnalyticsReadStore(DevDataSeed.seedEvents());
    }
}
```
- [ ] **Step 4: Run — PASS.** Dry-run: `mvn -q -pl services/analytics spring-boot:run`, then `curl -s localhost:8084/v1/stats/summary` and `/v1/events/samples` return seeded results with **no Mongo running**. Commit `feat(analytics): default to seeded in-memory read store for dry runs`.

---

### Task 5 (DEFERRED to the DB-candidate phase): Mongo read adapter

> ⏸ **Do NOT build now.** Per the standing in-memory-first directive, the Mongo
> adapter + Testcontainers integration land when we perfect the logic and compare
> DB candidates. Captured here so the seam is ready.

- `EnrichedEventDocument` (`@Document("events")`) — **must match `event-store`'s document shape** (`_id = eventId`, field names, indexes); confirm against event-store before writing (accepted §7 schema coupling — see `reconciliation.md`).
- `MongoAnalyticsReadStore implements AnalyticsReadStore` (`@Profile("mongo")`) — `MongoTemplate` aggregation for `summarize` (`$match` → `$group`/`$sortByCount`/`$limit`), `Query` + `Sort.by(DESC,"timestamp")` + skip/limit + count for `findSamples`. Read-only connection.
- Testcontainers `MongoAnalyticsReadStoreIT` — assert it matches the in-memory reference for the same seeded data.
- Add `spring-boot-starter-data-mongodb` + Testcontainers deps to the pom at this point.

---

### Optional bonuses touching this service

- **B3 time-series** (`GET /v1/stats/timeseries?...&interval={1m|5m|1h}`) — an `Interval` enum + bucketing use case + controller. See `docs/superpowers/plans/2026-07-07-bonuses.md`. OPTIONAL.
- **B4 rate-limiting** — a `RateLimitFilter` (429 per client IP) scoped to `/v1/stats/**` and `/v1/events/samples`. **These endpoints are owned here, so B4 is implemented in THIS service** (its spec lives in the bonuses plan). OPTIONAL — flag, don't block.

---

### Final: verify + tag
- [ ] `mvn -q -pl services/analytics verify` green. Dry-run on `:8084` with the **default (in-memory) profile** — no Mongo needed; `curl` both endpoints; confirm JSON shapes.
- [ ] Tag `v0.5-analytics`.

## Self-Review
- Stats + samples against the assignment's exact shapes, read-only, with the **in-memory read store as the runnable default (seeded)** so dry runs need no DB; Mongo deferred behind a profile to the DB phase (matches the in-memory-first directive).
- No placeholders; referenced types are all defined in Task 1.
- No `contracts` API drift: analytics reads Mongo/​in-memory documents and only borrows the shared **enums** from contracts — it does not touch `MessageEnvelope`/`EnrichedEventMessage`. If the event-store document schema changes, re-check Task 5's mapping (logged via `services/contracts/.claude/CHANGELOG.md` only if a shared type moves; the Mongo doc shape is an event-store concern to confirm at build time).
