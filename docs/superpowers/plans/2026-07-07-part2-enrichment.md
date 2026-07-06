# Part 2 — Classification & Enrichment Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Replace Part 1's placeholder enrichment with the real thing — classify each event's `attackType` and compute its 0–100 `threatScore` (including the stateful repeat-offender bonus) — so every ingested `SecurityEvent` is fully and correctly enriched.

**Architecture:** A composable **rule-object** scorer (the "Logic 2" design we chose): a `ScoringRule` interface plus one pure class per scoring contributor, summed and capped by `ThreatScoreCalculator`. Classification delegates to `AttackType.fromCategory`. The stateful repeat-offender signal is answered by the `OffenderWindow` port and injected into the pure scorer as a boolean. In-memory `EventStore` and `OffenderWindow` share one backing table (mirroring how a Mongo adapter would query one collection).

**Tech Stack:** Java 21, Spring Boot 3.3.5, JUnit 5 + AssertJ. Builds on Part 1 (`v0.1-ingestion`).

## Global Constraints

- **Java 21**, base package `com.akamai.wsa`, records for value types, immutability.
- **The `domain` package imports no Spring/Jackson/storage types.** The classifier and scorer implementations are pure domain classes; they are wired into Spring beans by a `@Configuration` in `infrastructure/config`. Only `infrastructure` adapters carry Spring annotations.
- **The scorer is a pure function** — the repeat-offender flag is computed by the caller and passed in via `ThreatScoringInputs`. No I/O in `ThreatScoreCalculator`.
- **Locked semantic decisions** (do not deviate — tests assert them):
  - Window basis = **`receivedAt`** (the trusted server clock), not event `timestamp`.
  - Count **prior persisted events only** — the current event (and others in the same batch) are not counted toward its own window.
  - **"> 5"** means the 6th-and-later event triggers the +15 (i.e. flag is `priorCount > 5`).
  - Window length = **10 minutes**; repeat-offender threshold = **5**. Named constants, no magic numbers.
- **Scoring weights** (assignment): severity CRITICAL 40 / HIGH 30 / MEDIUM 20 / LOW 10; action DENY +20 / ALERT +10 / MONITOR +0; sensitive path (`/admin` or `/login`) +15; repeat offender +15; **cap at 100**.
- Conventional commits, imperative, ≤72-char subject. Full descriptive parameter names (no `v`/`e`/`req` abbreviations).

---

### Task 1: Attack-type classifier implementation

**Files:**
- Create: `services/mini-wsa/src/main/java/com/akamai/wsa/domain/service/DefaultAttackTypeClassifier.java`
- Test: `services/mini-wsa/src/test/java/com/akamai/wsa/domain/service/DefaultAttackTypeClassifierTest.java`

**Interfaces:**
- Consumes: `AttackTypeClassifier` (interface), `AttackType.fromCategory`, `AttackCategory`.
- Produces: `DefaultAttackTypeClassifier implements AttackTypeClassifier` — a pure class (no Spring).

- [ ] **Step 1: Write the failing test**

```java
package com.akamai.wsa.domain.service;

import com.akamai.wsa.domain.model.AttackCategory;
import com.akamai.wsa.domain.model.AttackType;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class DefaultAttackTypeClassifierTest {

    private final AttackTypeClassifier classifier = new DefaultAttackTypeClassifier();

    @Test
    void classifiesEachCategory() {
        assertThat(classifier.classify(AttackCategory.INJECTION)).isEqualTo(AttackType.SQL_COMMAND_INJECTION);
        assertThat(classifier.classify(AttackCategory.BOT)).isEqualTo(AttackType.BOT_ACTIVITY);
        assertThat(classifier.classify(AttackCategory.RATE_LIMIT)).isEqualTo(AttackType.RATE_LIMITING);
    }
}
```

- [ ] **Step 2: Run it — expect FAIL** (`DefaultAttackTypeClassifier` does not exist)

Run: `mvn -q -pl services/mini-wsa test -Dtest=DefaultAttackTypeClassifierTest`

- [ ] **Step 3: Implement**

```java
package com.akamai.wsa.domain.service;

import com.akamai.wsa.domain.model.AttackCategory;
import com.akamai.wsa.domain.model.AttackType;

/**
 * Classifies a category into its human-readable attack type. Pure; wired as a
 * bean by infrastructure config.
 */
public final class DefaultAttackTypeClassifier implements AttackTypeClassifier {

    @Override
    public AttackType classify(AttackCategory attackCategory) {
        return AttackType.fromCategory(attackCategory);
    }
}
```

- [ ] **Step 4: Run it — expect PASS.**

- [ ] **Step 5: Commit** — `git commit -m "feat: add attack-type classifier implementation"`

---

### Task 2: Scoring rules (one pure class per contributor)

**Files:**
- Create: `services/mini-wsa/src/main/java/com/akamai/wsa/domain/service/ScoringRule.java`
- Create: `.../domain/service/SeverityRule.java`, `ActionRule.java`, `SensitivePathRule.java`, `RepeatOffenderRule.java`
- Test: `.../test/java/com/akamai/wsa/domain/service/ScoringRulesTest.java`

**Interfaces:**
- Consumes: `ThreatScoringInputs` (severity, action, requestPath, repeatOffender).
- Produces: `ScoringRule { int points(ThreatScoringInputs threatScoringInputs); }` and four implementations exposing their weights as public constants.

- [ ] **Step 1: Write the failing test**

```java
package com.akamai.wsa.domain.service;

import com.akamai.wsa.domain.model.Action;
import com.akamai.wsa.domain.model.Severity;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ScoringRulesTest {

    @Test
    void severityRuleScoresByTier() {
        assertThat(new SeverityRule().points(inputs(Severity.CRITICAL, Action.MONITOR, "/x", false))).isEqualTo(40);
        assertThat(new SeverityRule().points(inputs(Severity.HIGH, Action.MONITOR, "/x", false))).isEqualTo(30);
        assertThat(new SeverityRule().points(inputs(Severity.MEDIUM, Action.MONITOR, "/x", false))).isEqualTo(20);
        assertThat(new SeverityRule().points(inputs(Severity.LOW, Action.MONITOR, "/x", false))).isEqualTo(10);
    }

    @Test
    void actionRuleScoresByAction() {
        assertThat(new ActionRule().points(inputs(Severity.LOW, Action.DENY, "/x", false))).isEqualTo(20);
        assertThat(new ActionRule().points(inputs(Severity.LOW, Action.ALERT, "/x", false))).isEqualTo(10);
        assertThat(new ActionRule().points(inputs(Severity.LOW, Action.MONITOR, "/x", false))).isEqualTo(0);
    }

    @Test
    void sensitivePathRuleScoresLoginAndAdminPaths() {
        assertThat(new SensitivePathRule().points(inputs(Severity.LOW, Action.MONITOR, "/api/v1/login", false))).isEqualTo(15);
        assertThat(new SensitivePathRule().points(inputs(Severity.LOW, Action.MONITOR, "/admin/users", false))).isEqualTo(15);
        assertThat(new SensitivePathRule().points(inputs(Severity.LOW, Action.MONITOR, "/products", false))).isEqualTo(0);
    }

    @Test
    void repeatOffenderRuleScoresOnlyWhenFlagged() {
        assertThat(new RepeatOffenderRule().points(inputs(Severity.LOW, Action.MONITOR, "/x", true))).isEqualTo(15);
        assertThat(new RepeatOffenderRule().points(inputs(Severity.LOW, Action.MONITOR, "/x", false))).isEqualTo(0);
    }

    private static ThreatScoringInputs inputs(Severity severity, Action action, String requestPath, boolean repeatOffender) {
        return new ThreatScoringInputs(severity, action, requestPath, repeatOffender);
    }
}
```

- [ ] **Step 2: Run it — expect FAIL** (rule classes do not exist).

- [ ] **Step 3: Implement the interface and four rules**

`ScoringRule.java`
```java
package com.akamai.wsa.domain.service;

/** One additive contributor to the threat score. Pure. */
public interface ScoringRule {
    int points(ThreatScoringInputs threatScoringInputs);
}
```

`SeverityRule.java`
```java
package com.akamai.wsa.domain.service;

public final class SeverityRule implements ScoringRule {

    public static final int CRITICAL_POINTS = 40;
    public static final int HIGH_POINTS = 30;
    public static final int MEDIUM_POINTS = 20;
    public static final int LOW_POINTS = 10;

    @Override
    public int points(ThreatScoringInputs threatScoringInputs) {
        return switch (threatScoringInputs.severity()) {
            case CRITICAL -> CRITICAL_POINTS;
            case HIGH -> HIGH_POINTS;
            case MEDIUM -> MEDIUM_POINTS;
            case LOW -> LOW_POINTS;
        };
    }
}
```

`ActionRule.java`
```java
package com.akamai.wsa.domain.service;

public final class ActionRule implements ScoringRule {

    public static final int DENY_POINTS = 20;
    public static final int ALERT_POINTS = 10;
    public static final int MONITOR_POINTS = 0;

    @Override
    public int points(ThreatScoringInputs threatScoringInputs) {
        return switch (threatScoringInputs.action()) {
            case DENY -> DENY_POINTS;
            case ALERT -> ALERT_POINTS;
            case MONITOR -> MONITOR_POINTS;
        };
    }
}
```

`SensitivePathRule.java`
```java
package com.akamai.wsa.domain.service;

public final class SensitivePathRule implements ScoringRule {

    public static final int SENSITIVE_PATH_POINTS = 15;
    private static final String ADMIN_SEGMENT = "/admin";
    private static final String LOGIN_SEGMENT = "/login";

    @Override
    public int points(ThreatScoringInputs threatScoringInputs) {
        String requestPath = threatScoringInputs.requestPath();
        boolean sensitive = requestPath.contains(ADMIN_SEGMENT) || requestPath.contains(LOGIN_SEGMENT);
        return sensitive ? SENSITIVE_PATH_POINTS : 0;
    }
}
```

`RepeatOffenderRule.java`
```java
package com.akamai.wsa.domain.service;

public final class RepeatOffenderRule implements ScoringRule {

    public static final int REPEAT_OFFENDER_POINTS = 15;

    @Override
    public int points(ThreatScoringInputs threatScoringInputs) {
        return threatScoringInputs.repeatOffender() ? REPEAT_OFFENDER_POINTS : 0;
    }
}
```

- [ ] **Step 4: Run it — expect PASS.**

- [ ] **Step 5: Commit** — `git commit -m "feat: add composable threat-scoring rules"`

---

### Task 3: Rule-based threat-score calculator (the graded matrix)

**Files:**
- Create: `services/mini-wsa/src/main/java/com/akamai/wsa/domain/service/RuleBasedThreatScoreCalculator.java`
- Test: `.../test/java/com/akamai/wsa/domain/service/RuleBasedThreatScoreCalculatorTest.java`

**Interfaces:**
- Consumes: `ThreatScoreCalculator` (interface), `List<ScoringRule>`, `ThreatScore.ofCapped`.
- Produces: `RuleBasedThreatScoreCalculator implements ThreatScoreCalculator` — sums its rules and caps at 100.

- [ ] **Step 1: Write the failing test** (exhaustive; this is graded)

```java
package com.akamai.wsa.domain.service;

import com.akamai.wsa.domain.model.Action;
import com.akamai.wsa.domain.model.Severity;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class RuleBasedThreatScoreCalculatorTest {

    private final ThreatScoreCalculator calculator = new RuleBasedThreatScoreCalculator(
            List.of(new SeverityRule(), new ActionRule(), new SensitivePathRule(), new RepeatOffenderRule()));

    @Test
    void scoresTheAssignmentWorkedExample() {
        // CRITICAL(40) + DENY(20) + /login(15) + no repeat(0) = 75
        int score = calculator.calculate(
                new ThreatScoringInputs(Severity.CRITICAL, Action.DENY, "/api/v1/login", false)).value();
        assertThat(score).isEqualTo(75);
    }

    @Test
    void addsRepeatOffenderBonus() {
        // CRITICAL(40) + DENY(20) + /login(15) + repeat(15) = 90
        int score = calculator.calculate(
                new ThreatScoringInputs(Severity.CRITICAL, Action.DENY, "/api/v1/login", true)).value();
        assertThat(score).isEqualTo(90);
    }

    @Test
    void capsAtOneHundred() {
        // 40 + 20 + 15 + 15 = 90 is below cap; force >100 is impossible with these
        // weights, so verify the max realistic combo and the cap contract via ofCapped.
        int max = calculator.calculate(
                new ThreatScoringInputs(Severity.CRITICAL, Action.DENY, "/admin/login", true)).value();
        assertThat(max).isEqualTo(90);
    }

    @Test
    void scoresMinimalEvent() {
        // LOW(10) + MONITOR(0) + non-sensitive(0) + no repeat(0) = 10
        int score = calculator.calculate(
                new ThreatScoringInputs(Severity.LOW, Action.MONITOR, "/products", false)).value();
        assertThat(score).isEqualTo(10);
    }

    @Test
    void everySeverityTierContributes() {
        assertThat(scoreFor(Severity.CRITICAL)).isEqualTo(40);
        assertThat(scoreFor(Severity.HIGH)).isEqualTo(30);
        assertThat(scoreFor(Severity.MEDIUM)).isEqualTo(20);
        assertThat(scoreFor(Severity.LOW)).isEqualTo(10);
    }

    private int scoreFor(Severity severity) {
        return calculator.calculate(new ThreatScoringInputs(severity, Action.MONITOR, "/x", false)).value();
    }
}
```

> Note: with the assignment's weights the maximum reachable raw total is 90, so the cap never actually triggers in production. The cap contract itself is verified by `ThreatScoreTest.capsRawTotalsAtOneHundred()` (already present). If a future rule pushes totals over 100, add a direct cap test here.

- [ ] **Step 2: Run it — expect FAIL.**

- [ ] **Step 3: Implement**

```java
package com.akamai.wsa.domain.service;

import com.akamai.wsa.domain.model.ThreatScore;

import java.util.List;

/**
 * Sums a list of {@link ScoringRule}s and clamps the total into [0, 100].
 * Pure; the rule list is supplied at construction (wired by infrastructure config).
 */
public final class RuleBasedThreatScoreCalculator implements ThreatScoreCalculator {

    private final List<ScoringRule> scoringRules;

    public RuleBasedThreatScoreCalculator(List<ScoringRule> scoringRules) {
        this.scoringRules = List.copyOf(scoringRules);
    }

    @Override
    public ThreatScore calculate(ThreatScoringInputs threatScoringInputs) {
        int total = scoringRules.stream()
                .mapToInt(scoringRule -> scoringRule.points(threatScoringInputs))
                .sum();
        return ThreatScore.ofCapped(total);
    }
}
```

- [ ] **Step 4: Run it — expect PASS.**

- [ ] **Step 5: Commit** — `git commit -m "feat: add rule-based threat-score calculator"`

---

### Task 4: Repeat-offender window over a shared in-memory table

**Files:**
- Create: `services/mini-wsa/src/main/java/com/akamai/wsa/infrastructure/persistence/inmemory/InMemoryEventTable.java`
- Modify: `.../infrastructure/persistence/inmemory/InMemoryEventStore.java` (delegate to the shared table)
- Create: `.../infrastructure/persistence/inmemory/InMemoryOffenderWindow.java`
- Modify: `.../test/java/.../inmemory/InMemoryEventStoreTest.java` (construct with the table)
- Test: `.../test/java/.../inmemory/InMemoryOffenderWindowTest.java`

**Interfaces:**
- Consumes: `OffenderWindow` port, `SecurityEvent`, `ClientIp`, `TimeWindow`.
- Produces: `InMemoryEventTable` (shared list); `InMemoryEventStore` delegating to it; `InMemoryOffenderWindow implements OffenderWindow` counting over it. Both adapters see the same events, mirroring a single Mongo collection.

- [ ] **Step 1: Write the failing test**

```java
package com.akamai.wsa.infrastructure.persistence.inmemory;

import com.akamai.wsa.domain.model.ClientIp;
import com.akamai.wsa.domain.model.TimeWindow;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;

import static com.akamai.wsa.testsupport.SecurityEventFixtures.enrichedEventReceivedAt;
import static org.assertj.core.api.Assertions.assertThat;

class InMemoryOffenderWindowTest {

    private final InMemoryEventTable table = new InMemoryEventTable();
    private final InMemoryOffenderWindow offenderWindow = new InMemoryOffenderWindow(table);

    private static final ClientIp ATTACKER = new ClientIp("203.0.113.42");
    private static final Instant NOW = Instant.parse("2026-05-20T14:40:00Z");
    private static final TimeWindow TEN_MINUTES = TimeWindow.ofMinutes(10);

    @Test
    void countsOnlyEventsFromThatClientInsideTheWindow() {
        table.addAll(List.of(
                enrichedEventReceivedAt("evt-1", ATTACKER, NOW.minusSeconds(60)),   // inside
                enrichedEventReceivedAt("evt-2", ATTACKER, NOW.minusSeconds(300)),  // inside
                enrichedEventReceivedAt("evt-3", ATTACKER, NOW.minusSeconds(900)),  // 15 min ago -> outside
                enrichedEventReceivedAt("evt-4", new ClientIp("198.51.100.7"), NOW.minusSeconds(30)) // other IP
        ));

        long count = offenderWindow.countRecentEventsFromClient(ATTACKER, TEN_MINUTES, NOW);

        assertThat(count).isEqualTo(2);
    }

    @Test
    void includesTheWindowStartBoundaryAndExcludesEarlier() {
        table.addAll(List.of(
                enrichedEventReceivedAt("edge-in", ATTACKER, NOW.minusSeconds(600)),      // exactly 10 min -> inside
                enrichedEventReceivedAt("edge-out", ATTACKER, NOW.minusSeconds(601))       // just outside
        ));

        long count = offenderWindow.countRecentEventsFromClient(ATTACKER, TEN_MINUTES, NOW);

        assertThat(count).isEqualTo(1);
    }
}
```

Add this fixture overload to `com.akamai.wsa.testsupport.SecurityEventFixtures`:
```java
public static SecurityEvent enrichedEventReceivedAt(String eventId, ClientIp clientIp, Instant receivedAt) {
    DataLogRecord dataLogRecord = new DataLogRecord(
            eventId, receivedAt, 14227, "pol_web1", clientIp,
            "www.example.com", "/api/v1/login", "POST", 403, "Mozilla/5.0",
            new Rule("950001", "SQL_INJECTION", "SQL Injection Attack Detected",
                    Severity.CRITICAL, AttackCategory.INJECTION),
            Action.DENY, new GeoLocation("CN", "Beijing"), 1024, 256);
    return new SecurityEvent(dataLogRecord, AttackType.SQL_COMMAND_INJECTION, new ThreatScore(75), receivedAt);
}
```
(Import `ClientIp` in the fixture file.)

- [ ] **Step 2: Run it — expect FAIL** (`InMemoryEventTable`/`InMemoryOffenderWindow` missing).

- [ ] **Step 3: Create the shared table**

`InMemoryEventTable.java`
```java
package com.akamai.wsa.infrastructure.persistence.inmemory;

import com.akamai.wsa.domain.model.SecurityEvent;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Shared thread-safe in-memory event storage backing both the EventStore and
 * OffenderWindow adapters — so they observe the same events, exactly as a single
 * Mongo collection would.
 */
@Component
public class InMemoryEventTable {

    private final List<SecurityEvent> events = new CopyOnWriteArrayList<>();

    public void addAll(List<SecurityEvent> securityEvents) {
        events.addAll(securityEvents);
    }

    public List<SecurityEvent> all() {
        return List.copyOf(events);
    }

    public long size() {
        return events.size();
    }
}
```

- [ ] **Step 4: Refactor `InMemoryEventStore` to delegate to the table**

```java
package com.akamai.wsa.infrastructure.persistence.inmemory;

import com.akamai.wsa.domain.model.SecurityEvent;
import com.akamai.wsa.domain.port.EventStore;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public class InMemoryEventStore implements EventStore {

    private final InMemoryEventTable eventTable;

    public InMemoryEventStore(InMemoryEventTable eventTable) {
        this.eventTable = eventTable;
    }

    @Override
    public void saveAll(List<SecurityEvent> securityEvents) {
        eventTable.addAll(securityEvents);
    }

    @Override
    public long countAll() {
        return eventTable.size();
    }

    @Override
    public List<SecurityEvent> findByConfigId(int configId) {
        return eventTable.all().stream()
                .filter(securityEvent -> securityEvent.configId() == configId)
                .toList();
    }
}
```

Update `InMemoryEventStoreTest` construction to `new InMemoryEventStore(new InMemoryEventTable())`.

- [ ] **Step 5: Implement `InMemoryOffenderWindow`**

```java
package com.akamai.wsa.infrastructure.persistence.inmemory;

import com.akamai.wsa.domain.model.ClientIp;
import com.akamai.wsa.domain.model.TimeWindow;
import com.akamai.wsa.domain.port.OffenderWindow;
import org.springframework.stereotype.Repository;

import java.time.Instant;

@Repository
public class InMemoryOffenderWindow implements OffenderWindow {

    private final InMemoryEventTable eventTable;

    public InMemoryOffenderWindow(InMemoryEventTable eventTable) {
        this.eventTable = eventTable;
    }

    @Override
    public long countRecentEventsFromClient(ClientIp clientIp, TimeWindow timeWindow, Instant asOf) {
        Instant windowStart = asOf.minus(timeWindow.length());
        return eventTable.all().stream()
                .filter(securityEvent -> securityEvent.clientIp().equals(clientIp))
                .filter(securityEvent -> isWithinWindow(securityEvent.receivedAt(), windowStart, asOf))
                .count();
    }

    private boolean isWithinWindow(Instant receivedAt, Instant windowStart, Instant asOf) {
        return !receivedAt.isBefore(windowStart) && !receivedAt.isAfter(asOf);
    }
}
```

- [ ] **Step 6: Run the tests — expect PASS** (`InMemoryOffenderWindowTest`, `InMemoryEventStoreTest`).

Run: `mvn -q -pl services/mini-wsa test`

- [ ] **Step 7: Commit** — `git commit -m "feat: add repeat-offender window over shared in-memory table"`

---

### Task 5: Wire real enrichment into ingestion

**Files:**
- Create: `services/mini-wsa/src/main/java/com/akamai/wsa/infrastructure/config/EnrichmentConfiguration.java`
- Modify: `.../application/ingest/EventIngestionService.java` (from Part 1 — replace placeholder enrichment)
- Modify/Test: `.../test/java/.../application/ingest/EventIngestionServiceTest.java`

**Interfaces:**
- Consumes: `AttackTypeClassifier`, `ThreatScoreCalculator`, `OffenderWindow`, `EventStore`, `Clock`, `ThreatScoringInputs`, `TimeWindow`.
- Produces: beans for the pure classifier/calculator; an `EventIngestionService` that computes real `attackType`, `threatScore`, and repeat-offender flag per event.

- [ ] **Step 1: Add the wiring configuration (pure classes → beans)**

`EnrichmentConfiguration.java`
```java
package com.akamai.wsa.infrastructure.config;

import com.akamai.wsa.domain.service.ActionRule;
import com.akamai.wsa.domain.service.AttackTypeClassifier;
import com.akamai.wsa.domain.service.DefaultAttackTypeClassifier;
import com.akamai.wsa.domain.service.RepeatOffenderRule;
import com.akamai.wsa.domain.service.RuleBasedThreatScoreCalculator;
import com.akamai.wsa.domain.service.SensitivePathRule;
import com.akamai.wsa.domain.service.SeverityRule;
import com.akamai.wsa.domain.service.ThreatScoreCalculator;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;

@Configuration
public class EnrichmentConfiguration {

    @Bean
    public AttackTypeClassifier attackTypeClassifier() {
        return new DefaultAttackTypeClassifier();
    }

    @Bean
    public ThreatScoreCalculator threatScoreCalculator() {
        return new RuleBasedThreatScoreCalculator(List.of(
                new SeverityRule(),
                new ActionRule(),
                new SensitivePathRule(),
                new RepeatOffenderRule()));
    }
}
```

- [ ] **Step 2: Write the failing enrichment test** (extends the Part 1 service test)

```java
package com.akamai.wsa.application.ingest;

import com.akamai.wsa.domain.model.DataLogRecord;
import com.akamai.wsa.domain.service.DefaultAttackTypeClassifier;
import com.akamai.wsa.domain.service.RuleBasedThreatScoreCalculator;
import com.akamai.wsa.domain.service.ActionRule;
import com.akamai.wsa.domain.service.RepeatOffenderRule;
import com.akamai.wsa.domain.service.SensitivePathRule;
import com.akamai.wsa.domain.service.SeverityRule;
import com.akamai.wsa.infrastructure.persistence.inmemory.InMemoryEventStore;
import com.akamai.wsa.infrastructure.persistence.inmemory.InMemoryEventTable;
import com.akamai.wsa.infrastructure.persistence.inmemory.InMemoryOffenderWindow;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;

import static com.akamai.wsa.testsupport.DataLogRecordFixtures.record;
import static org.assertj.core.api.Assertions.assertThat;

class EventIngestionServiceTest {

    private final InMemoryEventTable table = new InMemoryEventTable();
    private final Clock fixedClock = Clock.fixed(Instant.parse("2026-05-20T14:40:00Z"), ZoneOffset.UTC);
    private final EventIngestionService service = new EventIngestionService(
            fixedClock,
            new InMemoryEventStore(table),
            new InMemoryOffenderWindow(table),
            new DefaultAttackTypeClassifier(),
            new RuleBasedThreatScoreCalculator(List.of(
                    new SeverityRule(), new ActionRule(), new SensitivePathRule(), new RepeatOffenderRule())));

    @Test
    void enrichesWithAttackTypeAndScoreAndReceivedAt() {
        var outcome = service.ingest(List.of(record("evt-1", "203.0.113.42", "/api/v1/login")));

        var stored = outcome.storedEvents().get(0);
        assertThat(stored.attackType().displayName()).isEqualTo("SQL/Command Injection");
        assertThat(stored.threatScore().value()).isEqualTo(75); // CRITICAL+DENY+/login, not yet a repeat offender
        assertThat(stored.receivedAt()).isEqualTo(Instant.parse("2026-05-20T14:40:00Z"));
    }

    @Test
    void appliesRepeatOffenderBonusOnceMoreThanFivePriorEventsExist() {
        String attackerIp = "203.0.113.42";
        // ingest 6 prior events from the same IP (each ingested separately so they persist first)
        for (int index = 0; index < 6; index++) {
            service.ingest(List.of(record("prior-" + index, attackerIp, "/api/v1/login")));
        }
        var outcome = service.ingest(List.of(record("evt-trigger", attackerIp, "/api/v1/login")));

        // 6 prior events exist within the window -> +15 repeat bonus -> 90
        assertThat(outcome.storedEvents().get(0).threatScore().value()).isEqualTo(90);
    }
}
```

Add a `DataLogRecordFixtures.record(eventId, clientIpValue, path)` helper in `testsupport` returning a CRITICAL/INJECTION/DENY `DataLogRecord` (mirror `SecurityEventFixtures.dataLogRecord`), so the test reads cleanly.

- [ ] **Step 3: Run it — expect FAIL** (constructor signature/behavior differ from Part 1's placeholder).

- [ ] **Step 4: Rewrite `EventIngestionService`**

```java
package com.akamai.wsa.application.ingest;

import com.akamai.wsa.domain.model.AttackType;
import com.akamai.wsa.domain.model.DataLogRecord;
import com.akamai.wsa.domain.model.SecurityEvent;
import com.akamai.wsa.domain.model.ThreatScore;
import com.akamai.wsa.domain.model.TimeWindow;
import com.akamai.wsa.domain.port.EventStore;
import com.akamai.wsa.domain.port.OffenderWindow;
import com.akamai.wsa.domain.service.AttackTypeClassifier;
import com.akamai.wsa.domain.service.ThreatScoreCalculator;
import com.akamai.wsa.domain.service.ThreatScoringInputs;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

@Service
public class EventIngestionService implements IngestSecurityEvents {

    static final TimeWindow REPEAT_OFFENDER_WINDOW = TimeWindow.ofMinutes(10);
    static final int REPEAT_OFFENDER_THRESHOLD = 5;

    private final Clock clock;
    private final EventStore eventStore;
    private final OffenderWindow offenderWindow;
    private final AttackTypeClassifier attackTypeClassifier;
    private final ThreatScoreCalculator threatScoreCalculator;

    public EventIngestionService(
            Clock clock,
            EventStore eventStore,
            OffenderWindow offenderWindow,
            AttackTypeClassifier attackTypeClassifier,
            ThreatScoreCalculator threatScoreCalculator) {
        this.clock = clock;
        this.eventStore = eventStore;
        this.offenderWindow = offenderWindow;
        this.attackTypeClassifier = attackTypeClassifier;
        this.threatScoreCalculator = threatScoreCalculator;
    }

    @Override
    public IngestionOutcome ingest(List<DataLogRecord> dataLogRecords) {
        Instant receivedAt = Instant.now(clock);
        List<SecurityEvent> enrichedEvents = new ArrayList<>(dataLogRecords.size());
        for (DataLogRecord dataLogRecord : dataLogRecords) {
            enrichedEvents.add(enrich(dataLogRecord, receivedAt));
        }
        eventStore.saveAll(enrichedEvents);
        return new IngestionOutcome(enrichedEvents.size(), List.copyOf(enrichedEvents));
    }

    private SecurityEvent enrich(DataLogRecord dataLogRecord, Instant receivedAt) {
        long priorEventCount = offenderWindow.countRecentEventsFromClient(
                dataLogRecord.clientIp(), REPEAT_OFFENDER_WINDOW, receivedAt);
        boolean repeatOffender = priorEventCount > REPEAT_OFFENDER_THRESHOLD;

        ThreatScoringInputs threatScoringInputs = new ThreatScoringInputs(
                dataLogRecord.rule().severity(),
                dataLogRecord.action(),
                dataLogRecord.path(),
                repeatOffender);

        ThreatScore threatScore = threatScoreCalculator.calculate(threatScoringInputs);
        AttackType attackType = attackTypeClassifier.classify(dataLogRecord.rule().category());
        return new SecurityEvent(dataLogRecord, attackType, threatScore, receivedAt);
    }
}
```

> Decision note (documented in code review): repeat-offender counts **prior persisted** events; events within the same batch do not count toward each other because the batch is enriched before it is saved. `receivedAt` is the single trusted timestamp for the batch.

- [ ] **Step 5: Run the full module test suite — expect PASS.**

Run: `mvn -q -pl services/mini-wsa test`

- [ ] **Step 6: Dry-run** — start on 8081, POST 6+ events from one IP hitting `/login`, GET the samples/ping, confirm the 6th+ event scores 90.

- [ ] **Step 7: Commit and tag**

```bash
git commit -m "feat: enrich events with attack type and threat score"
git tag v0.2-enrichment
```

---

## Self-Review

**Spec coverage:** attackType mapping ✓ (Task 1, + `AttackTypeTest`); threat score severity/action/path/repeat/cap ✓ (Tasks 2–3); repeat-offender >5-in-10-min with `receivedAt` basis and prior-only counting ✓ (Task 4–5); enriched event stored with all three fields ✓ (Task 5).

**Placeholder scan:** none — every step has complete code.

**Type consistency:** `ThreatScoringInputs(severity, action, requestPath, repeatOffender)`, `OffenderWindow.countRecentEventsFromClient(clientIp, timeWindow, asOf)`, `ThreatScoreCalculator.calculate(...)`, `AttackTypeClassifier.classify(...)`, `SecurityEvent(dataLogRecord, attackType, threatScore, receivedAt)` all match the committed interfaces. `EventIngestionService` constructor extends Part 1's (adds offenderWindow, classifier, calculator) — Part 1's DI wiring and its service test must be updated to the new constructor when this part lands (flagged cross-part dependency).

**Domain purity:** rules, calculator, and classifier carry no Spring imports; they become beans via `EnrichmentConfiguration` in `infrastructure/config`. Only adapters (`@Repository`) and the application service (`@Service`) are annotated.
