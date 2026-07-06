# Part 4 — Samples API Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Serve `GET /v1/events/samples` — a filtered, paginated, newest-first page of enriched events, plus the total count of matches.

**Architecture:** Extend the `EventStore` outbound port with a `findSamples` query; implement it in the in-memory adapter (filter → sort desc → total → page). A thin `FetchEventSamples` application service delegates to the port. The `SamplesController` parses/validates query params, clamps pagination, and maps results to the assignment's flat enriched-event JSON via a shared `SecurityEventResponse`.

**Tech Stack:** Java 21, Spring Boot 3.3.5, Maven, JUnit 5 + MockMvc + AssertJ.

## Global Constraints

- **Java version:** 21. Base package `com.akamai.wsa`.
- **`domain` package imports nothing from Spring/Jackson/storage.** The controller/DTOs live in `interfaces/rest`, the adapter in `infrastructure`.
- **Immutability:** records; no input mutation; full parameter names (no `v`/`e` abbreviations) including in lambdas.
- **Program to interfaces:** the controller depends on `FetchEventSamples`; the service depends on `EventStore`.
- **API response = assignment shape exactly** (no `{success,data}` envelope): `{ total, limit, offset, results: [ <flat enriched event> ] }`.
- **Pagination:** `limit` default 20 / max 100 (clamped via `SampleQuery.clampLimit`); `offset` ≥ 0 default 0; all filters optional.
- **Sort:** results ordered by event `timestamp` **descending** (newest first). `total` counts all matches, independent of the page window.
- **Commits:** conventional, imperative, ≤72 chars. Milestone tag `v0.4-samples` at the end.
- **Local dev port:** `8081`.

**Reuses existing types (already in the codebase):** `SampleQuery` (`application/samples`, with `DEFAULT_LIMIT=20`, `MAXIMUM_LIMIT=100`, `clampLimit(Integer)`), `EventSamplesPage` (`application/samples`), `FetchEventSamples` (interface, `application/samples`), `EventStore` (`domain/port`), `SecurityEvent` + accessors (`eventId/configId/clientIp/timestamp/path/action/category/dataLogRecord/attackType/threatScore/receivedAt`), `TimeRange.includes(Instant)`, and `com.akamai.wsa.testsupport.SecurityEventFixtures`.

---

### Task 1: Add `findSamples` to `EventStore` and implement it in-memory

**Files:**
- Modify: `services/mini-wsa/src/main/java/com/akamai/wsa/domain/port/EventStore.java`
- Modify: `services/mini-wsa/src/main/java/com/akamai/wsa/infrastructure/persistence/inmemory/InMemoryEventStore.java`
- Test: `services/mini-wsa/src/test/java/com/akamai/wsa/infrastructure/persistence/inmemory/InMemoryEventStoreSamplesTest.java`

**Interfaces:**
- Consumes: `SampleQuery`, `EventSamplesPage`, `SecurityEvent`, `TimeRange`.
- Produces: `EventSamplesPage EventStore.findSamples(SampleQuery sampleQuery)` — filtered, timestamp-descending, with `total` = full match count and `events` = the `offset`/`limit` window.

- [ ] **Step 1: Write the failing test**

`services/mini-wsa/src/test/java/com/akamai/wsa/infrastructure/persistence/inmemory/InMemoryEventStoreSamplesTest.java`
```java
package com.akamai.wsa.infrastructure.persistence.inmemory;

import com.akamai.wsa.application.samples.EventSamplesPage;
import com.akamai.wsa.application.samples.SampleQuery;
import com.akamai.wsa.domain.model.Action;
import com.akamai.wsa.domain.model.AttackCategory;
import com.akamai.wsa.domain.model.SecurityEvent;
import com.akamai.wsa.domain.model.TimeRange;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;

import static com.akamai.wsa.testsupport.SecurityEventFixtures.enrichedEventAt;
import static org.assertj.core.api.Assertions.assertThat;

class InMemoryEventStoreSamplesTest {

    private final InMemoryEventStore eventStore = new InMemoryEventStore();

    @Test
    void returnsMatchesNewestFirstWithTotalAndPageWindow() {
        eventStore.saveAll(List.of(
                enrichedEventAt("evt-old", 14227, "203.0.113.1", "/login",
                        AttackCategory.INJECTION, Action.DENY, Instant.parse("2026-05-20T10:00:00Z")),
                enrichedEventAt("evt-mid", 14227, "203.0.113.2", "/admin",
                        AttackCategory.BOT, Action.ALERT, Instant.parse("2026-05-20T11:00:00Z")),
                enrichedEventAt("evt-new", 14227, "203.0.113.3", "/api",
                        AttackCategory.XSS, Action.MONITOR, Instant.parse("2026-05-20T12:00:00Z"))
        ));

        EventSamplesPage page = eventStore.findSamples(
                new SampleQuery(null, TimeRange.unbounded(), null, null, 2, 0));

        assertThat(page.total()).isEqualTo(3);
        assertThat(page.events()).extracting(SecurityEvent::eventId)
                .containsExactly("evt-new", "evt-mid");
    }

    @Test
    void appliesOffsetForPaging() {
        eventStore.saveAll(List.of(
                enrichedEventAt("evt-old", 14227, "203.0.113.1", "/login",
                        AttackCategory.INJECTION, Action.DENY, Instant.parse("2026-05-20T10:00:00Z")),
                enrichedEventAt("evt-new", 14227, "203.0.113.3", "/api",
                        AttackCategory.XSS, Action.MONITOR, Instant.parse("2026-05-20T12:00:00Z"))
        ));

        EventSamplesPage page = eventStore.findSamples(
                new SampleQuery(null, TimeRange.unbounded(), null, null, 20, 1));

        assertThat(page.total()).isEqualTo(2);
        assertThat(page.events()).extracting(SecurityEvent::eventId).containsExactly("evt-old");
    }

    @Test
    void filtersByEveryDimension() {
        eventStore.saveAll(List.of(
                enrichedEventAt("evt-hit", 14227, "203.0.113.1", "/login",
                        AttackCategory.INJECTION, Action.DENY, Instant.parse("2026-05-20T11:00:00Z")),
                enrichedEventAt("evt-config", 99999, "203.0.113.2", "/login",
                        AttackCategory.INJECTION, Action.DENY, Instant.parse("2026-05-20T11:00:00Z")),
                enrichedEventAt("evt-category", 14227, "203.0.113.3", "/login",
                        AttackCategory.BOT, Action.DENY, Instant.parse("2026-05-20T11:00:00Z")),
                enrichedEventAt("evt-action", 14227, "203.0.113.4", "/login",
                        AttackCategory.INJECTION, Action.ALERT, Instant.parse("2026-05-20T11:00:00Z")),
                enrichedEventAt("evt-time", 14227, "203.0.113.5", "/login",
                        AttackCategory.INJECTION, Action.DENY, Instant.parse("2026-05-20T09:00:00Z"))
        ));

        EventSamplesPage page = eventStore.findSamples(new SampleQuery(
                14227,
                new TimeRange(Instant.parse("2026-05-20T10:00:00Z"), Instant.parse("2026-05-20T12:00:00Z")),
                AttackCategory.INJECTION,
                Action.DENY,
                20, 0));

        assertThat(page.events()).extracting(SecurityEvent::eventId).containsExactly("evt-hit");
        assertThat(page.total()).isEqualTo(1);
    }
}
```

- [ ] **Step 2: Add the test fixture overload it needs**

`SecurityEventFixtures` currently exposes `enrichedEvent(eventId, configId, clientIpValue, path)`. Add an overload that also varies category, action, and timestamp.

Append to `services/mini-wsa/src/test/java/com/akamai/wsa/testsupport/SecurityEventFixtures.java` (inside the class, and add the imports shown):
```java
    // add imports at top of file:
    // import com.akamai.wsa.domain.model.Rule;  (already present)
    // (Action, AttackCategory, AttackType, ClientIp, DataLogRecord, GeoLocation, Severity, ThreatScore already imported)

    public static SecurityEvent enrichedEventAt(
            String eventId,
            int configId,
            String clientIpValue,
            String path,
            AttackCategory category,
            Action action,
            Instant timestamp
    ) {
        DataLogRecord dataLogRecord = new DataLogRecord(
                eventId,
                timestamp,
                configId,
                "pol_web1",
                new ClientIp(clientIpValue),
                "www.example.com",
                path,
                "POST",
                403,
                "Mozilla/5.0",
                new Rule("950001", "RULE", "message", Severity.CRITICAL, category),
                action,
                new GeoLocation("CN", "Beijing"),
                1024,
                256
        );
        return new SecurityEvent(dataLogRecord, AttackType.fromCategory(category), new ThreatScore(75), timestamp);
    }
```

- [ ] **Step 3: Run the test to verify it fails**

Run: `mvn -q -pl services/mini-wsa test -Dtest=InMemoryEventStoreSamplesTest`
Expected: FAIL — `findSamples` is not defined on `EventStore`/`InMemoryEventStore` (compilation error).

- [ ] **Step 4: Add the port method**

In `services/mini-wsa/src/main/java/com/akamai/wsa/domain/port/EventStore.java`, add the import and method:
```java
import com.akamai.wsa.application.samples.EventSamplesPage;
import com.akamai.wsa.application.samples.SampleQuery;
```
```java
    EventSamplesPage findSamples(SampleQuery sampleQuery);
```

- [ ] **Step 5: Implement it in the in-memory adapter**

In `services/mini-wsa/src/main/java/com/akamai/wsa/infrastructure/persistence/inmemory/InMemoryEventStore.java`, add imports and the method:
```java
import com.akamai.wsa.application.samples.EventSamplesPage;
import com.akamai.wsa.application.samples.SampleQuery;
import java.util.Comparator;
```
```java
    @Override
    public EventSamplesPage findSamples(SampleQuery sampleQuery) {
        List<SecurityEvent> matches = events.stream()
                .filter(securityEvent -> sampleQuery.configId() == null
                        || securityEvent.configId() == sampleQuery.configId())
                .filter(securityEvent -> sampleQuery.timeRange().includes(securityEvent.timestamp()))
                .filter(securityEvent -> sampleQuery.category() == null
                        || securityEvent.category() == sampleQuery.category())
                .filter(securityEvent -> sampleQuery.action() == null
                        || securityEvent.action() == sampleQuery.action())
                .sorted(Comparator.comparing(SecurityEvent::timestamp).reversed())
                .toList();

        List<SecurityEvent> pageWindow = matches.stream()
                .skip(sampleQuery.offset())
                .limit(sampleQuery.limit())
                .toList();

        return new EventSamplesPage(matches.size(), sampleQuery.limit(), sampleQuery.offset(), pageWindow);
    }
```

- [ ] **Step 6: Run the test to verify it passes**

Run: `mvn -q -pl services/mini-wsa test -Dtest=InMemoryEventStoreSamplesTest`
Expected: PASS (3 tests).

- [ ] **Step 7: Commit**

```bash
git add services/mini-wsa/src/main/java/com/akamai/wsa/domain/port/EventStore.java \
        services/mini-wsa/src/main/java/com/akamai/wsa/infrastructure/persistence/inmemory/InMemoryEventStore.java \
        services/mini-wsa/src/test/java/com/akamai/wsa/infrastructure/persistence/inmemory/InMemoryEventStoreSamplesTest.java \
        services/mini-wsa/src/test/java/com/akamai/wsa/testsupport/SecurityEventFixtures.java
git commit -m "feat: add filtered paginated findSamples to the event store"
```

---

### Task 2: `FetchEventSamples` application service

**Files:**
- Create: `services/mini-wsa/src/main/java/com/akamai/wsa/application/samples/EventSamplesService.java`
- Test: `services/mini-wsa/src/test/java/com/akamai/wsa/application/samples/EventSamplesServiceTest.java`

**Interfaces:**
- Consumes: `EventStore.findSamples`.
- Produces: `EventSamplesService implements FetchEventSamples` — a Spring `@Service` delegating to the port. Thin, no business rules.

- [ ] **Step 1: Write the failing test**

`services/mini-wsa/src/test/java/com/akamai/wsa/application/samples/EventSamplesServiceTest.java`
```java
package com.akamai.wsa.application.samples;

import com.akamai.wsa.domain.model.TimeRange;
import com.akamai.wsa.domain.port.EventStore;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class EventSamplesServiceTest {

    @Test
    void delegatesToTheEventStore() {
        EventStore eventStore = mock(EventStore.class);
        SampleQuery sampleQuery = new SampleQuery(14227, TimeRange.unbounded(), null, null, 20, 0);
        EventSamplesPage expected = new EventSamplesPage(0, 20, 0, List.of());
        when(eventStore.findSamples(any(SampleQuery.class))).thenReturn(expected);

        EventSamplesService service = new EventSamplesService(eventStore);

        assertThat(service.fetch(sampleQuery)).isSameAs(expected);
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `mvn -q -pl services/mini-wsa test -Dtest=EventSamplesServiceTest`
Expected: FAIL — `EventSamplesService` does not exist.

- [ ] **Step 3: Implement the service**

`services/mini-wsa/src/main/java/com/akamai/wsa/application/samples/EventSamplesService.java`
```java
package com.akamai.wsa.application.samples;

import com.akamai.wsa.domain.port.EventStore;
import org.springframework.stereotype.Service;

@Service
public class EventSamplesService implements FetchEventSamples {

    private final EventStore eventStore;

    public EventSamplesService(EventStore eventStore) {
        this.eventStore = eventStore;
    }

    @Override
    public EventSamplesPage fetch(SampleQuery sampleQuery) {
        return eventStore.findSamples(sampleQuery);
    }
}
```

- [ ] **Step 4: Run test to verify it passes**

Run: `mvn -q -pl services/mini-wsa test -Dtest=EventSamplesServiceTest`
Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add services/mini-wsa/src/main/java/com/akamai/wsa/application/samples/EventSamplesService.java \
        services/mini-wsa/src/test/java/com/akamai/wsa/application/samples/EventSamplesServiceTest.java
git commit -m "feat: add FetchEventSamples application service"
```

---

### Task 3: `SamplesController` + response mapping + validation

**Files:**
- Create: `services/mini-wsa/src/main/java/com/akamai/wsa/interfaces/rest/SecurityEventResponse.java`
- Create: `services/mini-wsa/src/main/java/com/akamai/wsa/interfaces/rest/EventSamplesResponse.java`
- Create: `services/mini-wsa/src/main/java/com/akamai/wsa/interfaces/rest/SamplesController.java`
- Test: `services/mini-wsa/src/test/java/com/akamai/wsa/interfaces/rest/SamplesControllerTest.java`

**Interfaces:**
- Consumes: `FetchEventSamples.fetch`, `SampleQuery` (+ `clampLimit`), `EventSamplesPage`.
- Produces: `GET /v1/events/samples` returning `200 { total, limit, offset, results: [ flat enriched event ] }`; invalid `category`/`action` → `400`.

**Response shape (assignment):** each element in `results` is the flat enriched event — all original DLR fields plus `attackType` (the display name string), `threatScore` (the int), `receivedAt`.

- [ ] **Step 1: Write the failing test**

`services/mini-wsa/src/test/java/com/akamai/wsa/interfaces/rest/SamplesControllerTest.java`
```java
package com.akamai.wsa.interfaces.rest;

import com.akamai.wsa.application.samples.EventSamplesPage;
import com.akamai.wsa.application.samples.FetchEventSamples;
import com.akamai.wsa.application.samples.SampleQuery;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static com.akamai.wsa.testsupport.SecurityEventFixtures.enrichedEvent;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(SamplesController.class)
class SamplesControllerTest {

    @Autowired
    MockMvc mockMvc;

    @MockBean
    FetchEventSamples fetchEventSamples;

    @Test
    void returnsPagedEnrichedEventsInAssignmentShape() throws Exception {
        EventSamplesPage page = new EventSamplesPage(
                1, 20, 0,
                List.of(enrichedEvent("evt-00132", 14227, "203.0.113.42", "/api/v1/login")));
        when(fetchEventSamples.fetch(any(SampleQuery.class))).thenReturn(page);

        mockMvc.perform(get("/v1/events/samples").param("configId", "14227"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.total").value(1))
                .andExpect(jsonPath("$.limit").value(20))
                .andExpect(jsonPath("$.offset").value(0))
                .andExpect(jsonPath("$.results[0].eventId").value("evt-00132"))
                .andExpect(jsonPath("$.results[0].clientIp").value("203.0.113.42"))
                .andExpect(jsonPath("$.results[0].attackType").value("SQL/Command Injection"))
                .andExpect(jsonPath("$.results[0].threatScore").value(75))
                .andExpect(jsonPath("$.results[0].rule.category").value("INJECTION"));
    }

    @Test
    void rejectsInvalidCategoryWith400() throws Exception {
        mockMvc.perform(get("/v1/events/samples").param("category", "NOT_A_CATEGORY"))
                .andExpect(status().isBadRequest());
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `mvn -q -pl services/mini-wsa test -Dtest=SamplesControllerTest`
Expected: FAIL — controller/DTOs do not exist.

- [ ] **Step 3: Write the response DTOs**

`services/mini-wsa/src/main/java/com/akamai/wsa/interfaces/rest/SecurityEventResponse.java`
```java
package com.akamai.wsa.interfaces.rest;

import com.akamai.wsa.domain.model.DataLogRecord;
import com.akamai.wsa.domain.model.SecurityEvent;

import java.time.Instant;

/**
 * Flat wire representation of an enriched event — the exact shape the assignment
 * documents (original DLR fields + attackType + threatScore + receivedAt).
 * Shared by the samples endpoint and reused conceptually by other read endpoints.
 */
public record SecurityEventResponse(
        String eventId,
        Instant timestamp,
        int configId,
        String policyId,
        String clientIp,
        String hostname,
        String path,
        String method,
        int statusCode,
        String userAgent,
        RuleResponse rule,
        String action,
        GeoLocationResponse geoLocation,
        long requestSize,
        long responseSize,
        String attackType,
        int threatScore,
        Instant receivedAt
) {
    public record RuleResponse(String id, String name, String message, String severity, String category) {
    }

    public record GeoLocationResponse(String country, String city) {
    }

    public static SecurityEventResponse from(SecurityEvent securityEvent) {
        DataLogRecord dataLogRecord = securityEvent.dataLogRecord();
        GeoLocationResponse geoLocation = dataLogRecord.geoLocation() == null
                ? null
                : new GeoLocationResponse(dataLogRecord.geoLocation().country(), dataLogRecord.geoLocation().city());
        return new SecurityEventResponse(
                dataLogRecord.eventId(),
                dataLogRecord.timestamp(),
                dataLogRecord.configId(),
                dataLogRecord.policyId(),
                dataLogRecord.clientIp().value(),
                dataLogRecord.hostname(),
                dataLogRecord.path(),
                dataLogRecord.method(),
                dataLogRecord.statusCode(),
                dataLogRecord.userAgent(),
                new RuleResponse(
                        dataLogRecord.rule().id(),
                        dataLogRecord.rule().name(),
                        dataLogRecord.rule().message(),
                        dataLogRecord.rule().severity().name(),
                        dataLogRecord.rule().category().name()),
                dataLogRecord.action().name(),
                geoLocation,
                dataLogRecord.requestSize(),
                dataLogRecord.responseSize(),
                securityEvent.attackType().displayName(),
                securityEvent.threatScore().value(),
                securityEvent.receivedAt());
    }
}
```

`services/mini-wsa/src/main/java/com/akamai/wsa/interfaces/rest/EventSamplesResponse.java`
```java
package com.akamai.wsa.interfaces.rest;

import com.akamai.wsa.application.samples.EventSamplesPage;

import java.util.List;

public record EventSamplesResponse(
        long total,
        int limit,
        int offset,
        List<SecurityEventResponse> results
) {
    public static EventSamplesResponse from(EventSamplesPage eventSamplesPage) {
        List<SecurityEventResponse> results = eventSamplesPage.events().stream()
                .map(SecurityEventResponse::from)
                .toList();
        return new EventSamplesResponse(
                eventSamplesPage.total(),
                eventSamplesPage.limit(),
                eventSamplesPage.offset(),
                results);
    }
}
```

- [ ] **Step 4: Write the controller**

`services/mini-wsa/src/main/java/com/akamai/wsa/interfaces/rest/SamplesController.java`
```java
package com.akamai.wsa.interfaces.rest;

import com.akamai.wsa.application.samples.EventSamplesPage;
import com.akamai.wsa.application.samples.FetchEventSamples;
import com.akamai.wsa.application.samples.SampleQuery;
import com.akamai.wsa.domain.model.Action;
import com.akamai.wsa.domain.model.AttackCategory;
import com.akamai.wsa.domain.model.TimeRange;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;

@RestController
@RequestMapping("/v1/events")
class SamplesController {

    private final FetchEventSamples fetchEventSamples;

    SamplesController(FetchEventSamples fetchEventSamples) {
        this.fetchEventSamples = fetchEventSamples;
    }

    @GetMapping("/samples")
    EventSamplesResponse samples(
            @RequestParam(required = false) Integer configId,
            @RequestParam(required = false) String from,
            @RequestParam(required = false) String to,
            @RequestParam(required = false) String category,
            @RequestParam(required = false) String action,
            @RequestParam(required = false) Integer limit,
            @RequestParam(defaultValue = "0") int offset
    ) {
        SampleQuery sampleQuery = new SampleQuery(
                configId,
                new TimeRange(parseInstant(from), parseInstant(to)),
                parseCategory(category),
                parseAction(action),
                SampleQuery.clampLimit(limit),
                Math.max(0, offset));

        EventSamplesPage page = fetchEventSamples.fetch(sampleQuery);
        return EventSamplesResponse.from(page);
    }

    private static Instant parseInstant(String value) {
        return value == null ? null : Instant.parse(value);
    }

    private static AttackCategory parseCategory(String value) {
        return value == null ? null : AttackCategory.valueOf(value);
    }

    private static Action parseAction(String value) {
        return value == null ? null : Action.valueOf(value);
    }
}
```

> Note: `AttackCategory.valueOf`/`Action.valueOf`/`Instant.parse` throw `IllegalArgumentException`/`DateTimeParseException` on bad input. Task 4 maps those to `400`. (If Part 1's `@ControllerAdvice` already maps `IllegalArgumentException` → 400, this test passes without Task 4; keep Task 4 only if that advice does not yet exist.)

- [ ] **Step 5: Run test to verify it passes**

Run: `mvn -q -pl services/mini-wsa test -Dtest=SamplesControllerTest`
Expected: PASS (both tests). If `rejectsInvalidCategoryWith400` fails with 500, do Task 4.

- [ ] **Step 6: Commit**

```bash
git add services/mini-wsa/src/main/java/com/akamai/wsa/interfaces/rest/SecurityEventResponse.java \
        services/mini-wsa/src/main/java/com/akamai/wsa/interfaces/rest/EventSamplesResponse.java \
        services/mini-wsa/src/main/java/com/akamai/wsa/interfaces/rest/SamplesController.java \
        services/mini-wsa/src/test/java/com/akamai/wsa/interfaces/rest/SamplesControllerTest.java
git commit -m "feat: add samples endpoint with filters and pagination"
```

---

### Task 4 (conditional): map bad query params to 400

Only needed if a shared `@ControllerAdvice` mapping `IllegalArgumentException`/`DateTimeParseException` → 400 does not already exist from Part 1.

**Files:**
- Create/Modify: `services/mini-wsa/src/main/java/com/akamai/wsa/interfaces/rest/ApiExceptionHandler.java`

- [ ] **Step 1: Add/extend the advice**

```java
package com.akamai.wsa.interfaces.rest;

import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.time.format.DateTimeParseException;

@RestControllerAdvice
class ApiExceptionHandler {

    @ExceptionHandler({IllegalArgumentException.class, DateTimeParseException.class})
    ProblemDetail handleBadRequest(RuntimeException exception) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, exception.getMessage());
    }
}
```

- [ ] **Step 2: Verify + commit**

Run: `mvn -q -pl services/mini-wsa test -Dtest=SamplesControllerTest`
Expected: PASS.
```bash
git add services/mini-wsa/src/main/java/com/akamai/wsa/interfaces/rest/ApiExceptionHandler.java
git commit -m "feat: map invalid query params to 400"
```

---

### Final: full verification + milestone

- [ ] **Step 1: Full build**

Run: `mvn -q clean verify`
Expected: BUILD SUCCESS, all tests green.

- [ ] **Step 2: Manual dry-run** (optional, needs data ingested first)

```bash
mvn -q -pl services/mini-wsa spring-boot:run --% --server.port=8081 &
curl -s "localhost:8081/v1/events/samples?limit=5" | jq
```
Expected: `{ "total": <n>, "limit": 5, "offset": 0, "results": [ ... ] }`.

- [ ] **Step 3: Tag**

```bash
git tag v0.4-samples
```

---

## Self-Review

**Spec coverage:** filters configId/from/to/category/action all optional ✓ (Task 3); pagination limit default 20 / max 100 + offset ✓ (`clampLimit`, Task 3); newest-first sort ✓ (Task 1 Step 5); total count for pagination ✓ (Task 1); flat enriched-event response ✓ (`SecurityEventResponse`).

**Placeholder scan:** none — every step has concrete code/commands.

**Type consistency:** `findSamples(SampleQuery) → EventSamplesPage` matches the port, adapter, service, and controller; `SecurityEvent` accessors (`eventId/configId/timestamp/path/action/category/dataLogRecord/attackType/threatScore/receivedAt`) match the committed aggregate; `SampleQuery.clampLimit` and the `DEFAULT_LIMIT`/`MAXIMUM_LIMIT` constants are used as defined; the new `SecurityEventFixtures.enrichedEventAt(...)` overload is added in Task 1 Step 2 before its first use.

**Dependency note:** Task 4 (400 mapping) overlaps with Part 1's `@ControllerAdvice`. If Part 1 lands first, skip Task 4 and rely on its advice; the `SamplesControllerTest.rejectsInvalidCategoryWith400` case verifies whichever provides it.
