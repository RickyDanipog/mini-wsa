# enrichment service — Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development or superpowers:executing-plans. Steps use checkbox (`- [ ]`) syntax.

**Goal:** Build the `enrichment` service — consume raw events from Kafka, classify the attack type, compute the 0–100 threat score (including the Redis-backed repeat-offender bonus), and publish enriched events back to Kafka.

**Architecture:** Hexagonal inside the service. A **pure** domain core (classifier + composable rule-object scorer) behind ports; a `@KafkaListener` inbound adapter on `events.raw`; a Kafka producer + a Redis `OffenderWindow` adapter as outbound adapters. Domain imports no Spring/Kafka/Redis. Wired by a `@Configuration`.

**Tech Stack:** Java 21, Spring Boot 3.3.5, `spring-kafka`, `spring-data-redis`, JUnit 5 + AssertJ, `@EmbeddedKafka`. Depends on the `contracts` module (`RawEventMessage`, `EnrichedEventMessage`, `MessageEnvelope`, shared enums `AttackCategory`/`Action`/`Severity`/`AttackType`).

## Global Constraints

- Base package: `com.akamai.wsa.enrichment`. Service port `:8082`.
- Consumes topic **`events.raw`**; publishes topic **`events.enriched`** (keyed by `configId`). Topic names, window length, and threshold come from `application.yml`, not literals.
- **The `domain` package imports no Spring/Kafka/Redis types.** The classifier, rules, and calculator are pure classes wired into beans by `infrastructure/config/EnrichmentConfiguration`. Only adapters carry Spring annotations.
- **Scorer is pure** — the repeat-offender flag is computed by the listener and injected via `ThreatScoringInputs`.
- **Locked repeat-offender semantics:** window basis = **`receivedAt`** (trusted clock, injected via `Clock`); window length **10 minutes**; **record-then-read** ordering so the count *includes* the current event, and the flag is **`count > 5`** — i.e. the **6th** event from an IP within the window is the first one flagged (6 events now exist → "more than 5 exist"). `correlationId` is preserved from the inbound envelope onto the outbound message.
- **Scoring weights** (assignment): severity CRITICAL 40 / HIGH 30 / MEDIUM 20 / LOW 10; action DENY +20 / ALERT +10 / MONITOR +0; sensitive path (`/admin` or `/login`) +15; repeat offender +15; **cap at 100**. Named constants.
- Records, immutability, intention-revealing names, FULL descriptive parameter names (no `v`/`e`/`req`). Conventional commits.

---

### Task 1: Attack-type classifier (pure)

**Files:**
- Create: `services/enrichment/src/main/java/com/akamai/wsa/enrichment/domain/service/AttackTypeClassifier.java`
- Create: `.../domain/service/DefaultAttackTypeClassifier.java`
- Test: `.../test/java/com/akamai/wsa/enrichment/domain/service/DefaultAttackTypeClassifierTest.java`

**Interfaces:**
- Consumes: shared `com.akamai.wsa.contracts.AttackCategory`, `AttackType` (with `fromCategory` + `displayName`).
- Produces: `AttackTypeClassifier { AttackType classify(AttackCategory attackCategory); }` and `DefaultAttackTypeClassifier`.

- [ ] **Step 1: Write the failing test**

```java
package com.akamai.wsa.enrichment.domain.service;

import com.akamai.wsa.contracts.AttackCategory;
import com.akamai.wsa.contracts.AttackType;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class DefaultAttackTypeClassifierTest {

    private final AttackTypeClassifier attackTypeClassifier = new DefaultAttackTypeClassifier();

    @Test
    void classifiesEachCategory() {
        assertThat(attackTypeClassifier.classify(AttackCategory.INJECTION)).isEqualTo(AttackType.SQL_COMMAND_INJECTION);
        assertThat(attackTypeClassifier.classify(AttackCategory.BOT)).isEqualTo(AttackType.BOT_ACTIVITY);
        assertThat(attackTypeClassifier.classify(AttackCategory.RATE_LIMIT)).isEqualTo(AttackType.RATE_LIMITING);
    }
}
```

- [ ] **Step 2: Run — expect FAIL.** `mvn -q -pl services/enrichment test -Dtest=DefaultAttackTypeClassifierTest`

- [ ] **Step 3: Implement**

`AttackTypeClassifier.java`
```java
package com.akamai.wsa.enrichment.domain.service;

import com.akamai.wsa.contracts.AttackCategory;
import com.akamai.wsa.contracts.AttackType;

public interface AttackTypeClassifier {
    AttackType classify(AttackCategory attackCategory);
}
```

`DefaultAttackTypeClassifier.java`
```java
package com.akamai.wsa.enrichment.domain.service;

import com.akamai.wsa.contracts.AttackCategory;
import com.akamai.wsa.contracts.AttackType;

public final class DefaultAttackTypeClassifier implements AttackTypeClassifier {

    @Override
    public AttackType classify(AttackCategory attackCategory) {
        return AttackType.fromCategory(attackCategory);
    }
}
```

- [ ] **Step 4: Run — expect PASS.**
- [ ] **Step 5: Commit** — `feat(enrichment): add attack-type classifier`

---

### Task 2: Threat-score value object and scoring inputs

**Files:**
- Create: `.../domain/model/ThreatScore.java`
- Create: `.../domain/service/ThreatScoringInputs.java`
- Test: `.../test/java/com/akamai/wsa/enrichment/domain/model/ThreatScoreTest.java`

**Interfaces:**
- Produces: `ThreatScore(int value)` VO with `[0,100]` invariant and `ofCapped(int rawScore)`; `ThreatScoringInputs(Severity severity, Action action, String requestPath, boolean repeatOffender)`.

- [ ] **Step 1: Write the failing test**

```java
package com.akamai.wsa.enrichment.domain.model;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ThreatScoreTest {

    @Test
    void acceptsInRangeAndCaps() {
        assertThat(new ThreatScore(0).value()).isZero();
        assertThat(new ThreatScore(100).value()).isEqualTo(100);
        assertThat(ThreatScore.ofCapped(130).value()).isEqualTo(100);
        assertThat(ThreatScore.ofCapped(-4).value()).isZero();
    }

    @Test
    void rejectsOutOfRange() {
        assertThatThrownBy(() -> new ThreatScore(101)).isInstanceOf(IllegalArgumentException.class);
    }
}
```

- [ ] **Step 2: Run — expect FAIL.**

- [ ] **Step 3: Implement**

`ThreatScore.java`
```java
package com.akamai.wsa.enrichment.domain.model;

public record ThreatScore(int value) {

    public static final int MINIMUM = 0;
    public static final int MAXIMUM = 100;

    public ThreatScore {
        if (value < MINIMUM || value > MAXIMUM) {
            throw new IllegalArgumentException("threatScore must be within [" + MINIMUM + ", " + MAXIMUM + "]");
        }
    }

    public static ThreatScore ofCapped(int rawScore) {
        return new ThreatScore(Math.max(MINIMUM, Math.min(MAXIMUM, rawScore)));
    }
}
```

`ThreatScoringInputs.java`
```java
package com.akamai.wsa.enrichment.domain.service;

import com.akamai.wsa.contracts.Action;
import com.akamai.wsa.contracts.Severity;

public record ThreatScoringInputs(Severity severity, Action action, String requestPath, boolean repeatOffender) {
    public ThreatScoringInputs {
        if (severity == null) {
            throw new IllegalArgumentException("severity must not be null");
        }
        if (action == null) {
            throw new IllegalArgumentException("action must not be null");
        }
        if (requestPath == null) {
            throw new IllegalArgumentException("requestPath must not be null");
        }
    }
}
```

- [ ] **Step 4: Run — expect PASS.**
- [ ] **Step 5: Commit** — `feat(enrichment): add threat-score value object and scoring inputs`

---

### Task 3: Composable scoring rules

**Files:**
- Create: `.../domain/service/ScoringRule.java`, `SeverityRule.java`, `ActionRule.java`, `SensitivePathRule.java`, `RepeatOffenderRule.java`
- Test: `.../test/java/com/akamai/wsa/enrichment/domain/service/ScoringRulesTest.java`

**Interfaces:**
- Produces: `ScoringRule { int points(ThreatScoringInputs threatScoringInputs); }` + four pure implementations exposing weights as public constants.

- [ ] **Step 1: Write the failing test**

```java
package com.akamai.wsa.enrichment.domain.service;

import com.akamai.wsa.contracts.Action;
import com.akamai.wsa.contracts.Severity;
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
    void sensitivePathRuleScoresAdminAndLogin() {
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

- [ ] **Step 2: Run — expect FAIL.**

- [ ] **Step 3: Implement**

`ScoringRule.java`
```java
package com.akamai.wsa.enrichment.domain.service;

public interface ScoringRule {
    int points(ThreatScoringInputs threatScoringInputs);
}
```

`SeverityRule.java`
```java
package com.akamai.wsa.enrichment.domain.service;

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
package com.akamai.wsa.enrichment.domain.service;

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
package com.akamai.wsa.enrichment.domain.service;

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
package com.akamai.wsa.enrichment.domain.service;

public final class RepeatOffenderRule implements ScoringRule {

    public static final int REPEAT_OFFENDER_POINTS = 15;

    @Override
    public int points(ThreatScoringInputs threatScoringInputs) {
        return threatScoringInputs.repeatOffender() ? REPEAT_OFFENDER_POINTS : 0;
    }
}
```

- [ ] **Step 4: Run — expect PASS.**
- [ ] **Step 5: Commit** — `feat(enrichment): add composable scoring rules`

---

### Task 4: Rule-based threat-score calculator (graded matrix)

**Files:**
- Create: `.../domain/service/ThreatScoreCalculator.java`, `RuleBasedThreatScoreCalculator.java`
- Test: `.../test/java/com/akamai/wsa/enrichment/domain/service/RuleBasedThreatScoreCalculatorTest.java`

**Interfaces:**
- Produces: `ThreatScoreCalculator { ThreatScore calculate(ThreatScoringInputs threatScoringInputs); }` and `RuleBasedThreatScoreCalculator(List<ScoringRule>)` which sums and caps.

- [ ] **Step 1: Write the failing test** (exhaustive; graded logic)

```java
package com.akamai.wsa.enrichment.domain.service;

import com.akamai.wsa.contracts.Action;
import com.akamai.wsa.contracts.Severity;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class RuleBasedThreatScoreCalculatorTest {

    private final ThreatScoreCalculator calculator = new RuleBasedThreatScoreCalculator(List.of(
            new SeverityRule(), new ActionRule(), new SensitivePathRule(), new RepeatOffenderRule()));

    @Test
    void workedExample_criticalDenyLogin_noRepeat_is75() {
        assertThat(calculator.calculate(
                new ThreatScoringInputs(Severity.CRITICAL, Action.DENY, "/api/v1/login", false)).value()).isEqualTo(75);
    }

    @Test
    void withRepeatOffender_is90() {
        assertThat(calculator.calculate(
                new ThreatScoringInputs(Severity.CRITICAL, Action.DENY, "/api/v1/login", true)).value()).isEqualTo(90);
    }

    @Test
    void minimalEvent_is10() {
        assertThat(calculator.calculate(
                new ThreatScoringInputs(Severity.LOW, Action.MONITOR, "/products", false)).value()).isEqualTo(10);
    }

    @Test
    void everySeverityTierContributes() {
        assertThat(score(Severity.CRITICAL)).isEqualTo(40);
        assertThat(score(Severity.HIGH)).isEqualTo(30);
        assertThat(score(Severity.MEDIUM)).isEqualTo(20);
        assertThat(score(Severity.LOW)).isEqualTo(10);
    }

    private int score(Severity severity) {
        return calculator.calculate(new ThreatScoringInputs(severity, Action.MONITOR, "/x", false)).value();
    }
}
```

> Note: with the assignment weights the max reachable total is 90, so the cap never fires in production; the cap contract is verified directly in `ThreatScoreTest` (Task 2).

- [ ] **Step 2: Run — expect FAIL.**

- [ ] **Step 3: Implement**

`ThreatScoreCalculator.java`
```java
package com.akamai.wsa.enrichment.domain.service;

import com.akamai.wsa.enrichment.domain.model.ThreatScore;

public interface ThreatScoreCalculator {
    ThreatScore calculate(ThreatScoringInputs threatScoringInputs);
}
```

`RuleBasedThreatScoreCalculator.java`
```java
package com.akamai.wsa.enrichment.domain.service;

import com.akamai.wsa.enrichment.domain.model.ThreatScore;

import java.util.List;

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

- [ ] **Step 4: Run — expect PASS.**
- [ ] **Step 5: Commit** — `feat(enrichment): add rule-based threat-score calculator`

---

### Task 5: OffenderWindow port + in-memory adapter

**Files:**
- Create: `.../domain/port/OffenderWindow.java`
- Create: `.../infrastructure/offender/InMemoryOffenderWindow.java`
- Test: `.../test/java/com/akamai/wsa/enrichment/infrastructure/offender/InMemoryOffenderWindowTest.java`

**Interfaces:**
- Produces: `OffenderWindow` with `void recordEvent(ClientIp clientIp, Instant occurredAt)` and `long countRecentEventsFromClient(ClientIp clientIp, TimeWindow timeWindow, Instant asOf)`. A `ClientIp(String value)` VO and `TimeWindow(Duration length)` VO live in `.../domain/model` (create them here if not already present).

- [ ] **Step 1: Write the failing test** (record-then-read semantics; boundaries)

```java
package com.akamai.wsa.enrichment.infrastructure.offender;

import com.akamai.wsa.enrichment.domain.model.ClientIp;
import com.akamai.wsa.enrichment.domain.model.TimeWindow;
import com.akamai.wsa.enrichment.domain.port.OffenderWindow;
import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

class InMemoryOffenderWindowTest {

    private final OffenderWindow offenderWindow = new InMemoryOffenderWindow();
    private static final ClientIp ATTACKER = new ClientIp("203.0.113.42");
    private static final TimeWindow TEN_MINUTES = TimeWindow.ofMinutes(10);

    @Test
    void countsRecordedEventsInsideWindowForThatClient() {
        Instant now = Instant.parse("2026-05-20T14:40:00Z");
        offenderWindow.recordEvent(ATTACKER, now.minusSeconds(60));
        offenderWindow.recordEvent(ATTACKER, now.minusSeconds(300));
        offenderWindow.recordEvent(ATTACKER, now.minusSeconds(900));            // 15 min ago -> outside
        offenderWindow.recordEvent(new ClientIp("198.51.100.7"), now.minusSeconds(30)); // other IP

        assertThat(offenderWindow.countRecentEventsFromClient(ATTACKER, TEN_MINUTES, now)).isEqualTo(2);
    }

    @Test
    void sixthEventIsTheFirstToCountAboveFive() {
        Instant now = Instant.parse("2026-05-20T14:40:00Z");
        for (int index = 0; index < 6; index++) {
            offenderWindow.recordEvent(ATTACKER, now.minusSeconds(index)); // record current then read
        }
        // 6 events now exist within the window -> "more than 5"
        assertThat(offenderWindow.countRecentEventsFromClient(ATTACKER, TEN_MINUTES, now)).isEqualTo(6);
    }

    @Test
    void includesWindowStartBoundary() {
        Instant now = Instant.parse("2026-05-20T14:40:00Z");
        offenderWindow.recordEvent(ATTACKER, now.minusSeconds(600)); // exactly 10 min -> inside
        offenderWindow.recordEvent(ATTACKER, now.minusSeconds(601)); // just outside

        assertThat(offenderWindow.countRecentEventsFromClient(ATTACKER, TEN_MINUTES, now)).isEqualTo(1);
    }
}
```

- [ ] **Step 2: Run — expect FAIL.**

- [ ] **Step 3: Create the value objects** (if absent)

`ClientIp.java`
```java
package com.akamai.wsa.enrichment.domain.model;

public record ClientIp(String value) {
    public ClientIp {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("clientIp must not be blank");
        }
    }
}
```

`TimeWindow.java`
```java
package com.akamai.wsa.enrichment.domain.model;

import java.time.Duration;

public record TimeWindow(Duration length) {
    public TimeWindow {
        if (length == null || length.isNegative() || length.isZero()) {
            throw new IllegalArgumentException("time window length must be positive");
        }
    }

    public static TimeWindow ofMinutes(long minutes) {
        return new TimeWindow(Duration.ofMinutes(minutes));
    }
}
```

- [ ] **Step 4: Create the port**

`OffenderWindow.java`
```java
package com.akamai.wsa.enrichment.domain.port;

import com.akamai.wsa.enrichment.domain.model.ClientIp;
import com.akamai.wsa.enrichment.domain.model.TimeWindow;

import java.time.Instant;

/**
 * Records events per client and answers how many fall inside a look-back window.
 * Enrichment records the current event, then reads the count (which therefore
 * includes it) — so the 6th event within the window is the first with count > 5.
 */
public interface OffenderWindow {

    void recordEvent(ClientIp clientIp, Instant occurredAt);

    long countRecentEventsFromClient(ClientIp clientIp, TimeWindow timeWindow, Instant asOf);
}
```

- [ ] **Step 5: Implement the in-memory adapter**

`InMemoryOffenderWindow.java`
```java
package com.akamai.wsa.enrichment.infrastructure.offender;

import com.akamai.wsa.enrichment.domain.model.ClientIp;
import com.akamai.wsa.enrichment.domain.model.TimeWindow;
import com.akamai.wsa.enrichment.domain.port.OffenderWindow;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

/** Test/dev adapter; production uses {@code RedisOffenderWindow}. */
public class InMemoryOffenderWindow implements OffenderWindow {

    private final Map<ClientIp, List<Instant>> occurrencesByClient = new ConcurrentHashMap<>();

    @Override
    public void recordEvent(ClientIp clientIp, Instant occurredAt) {
        occurrencesByClient.computeIfAbsent(clientIp, ignored -> new CopyOnWriteArrayList<>()).add(occurredAt);
    }

    @Override
    public long countRecentEventsFromClient(ClientIp clientIp, TimeWindow timeWindow, Instant asOf) {
        Instant windowStart = asOf.minus(timeWindow.length());
        return occurrencesByClient.getOrDefault(clientIp, List.of()).stream()
                .filter(occurredAt -> !occurredAt.isBefore(windowStart) && !occurredAt.isAfter(asOf))
                .count();
    }
}
```

- [ ] **Step 6: Run — expect PASS.**
- [ ] **Step 7: Commit** — `feat(enrichment): add offender window port and in-memory adapter`

---

### Task 6: Redis OffenderWindow adapter

**Files:**
- Create: `.../infrastructure/offender/RedisOffenderWindow.java`
- Test: `.../test/java/com/akamai/wsa/enrichment/infrastructure/offender/RedisOffenderWindowTest.java` (Testcontainers Redis)

**Interfaces:**
- Consumes: `StringRedisTemplate`. Produces: `RedisOffenderWindow implements OffenderWindow` using a per-IP sorted set scored by epoch-milli.

- [ ] **Step 1: Write the failing Testcontainers test**

```java
package com.akamai.wsa.enrichment.infrastructure.offender;

import com.akamai.wsa.enrichment.domain.model.ClientIp;
import com.akamai.wsa.enrichment.domain.model.TimeWindow;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@Testcontainers
class RedisOffenderWindowTest {

    @Container
    static final GenericContainer<?> redis = new GenericContainer<>("redis:7").withExposedPorts(6379);

    @DynamicPropertySource
    static void redisProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.data.redis.host", redis::getHost);
        registry.add("spring.data.redis.port", () -> redis.getMappedPort(6379));
    }

    @Autowired
    StringRedisTemplate stringRedisTemplate;

    @Test
    void recordsAndCountsInsideWindow() {
        RedisOffenderWindow offenderWindow = new RedisOffenderWindow(stringRedisTemplate);
        ClientIp attacker = new ClientIp("203.0.113.42");
        Instant now = Instant.parse("2026-05-20T14:40:00Z");

        offenderWindow.recordEvent(attacker, now.minusSeconds(60));
        offenderWindow.recordEvent(attacker, now.minusSeconds(120));
        offenderWindow.recordEvent(attacker, now.minusSeconds(900)); // outside

        assertThat(offenderWindow.countRecentEventsFromClient(attacker, TimeWindow.ofMinutes(10), now)).isEqualTo(2);
    }
}
```

- [ ] **Step 2: Run — expect FAIL.** (Requires Docker for Testcontainers.)

- [ ] **Step 3: Implement**

`RedisOffenderWindow.java`
```java
package com.akamai.wsa.enrichment.infrastructure.offender;

import com.akamai.wsa.enrichment.domain.model.ClientIp;
import com.akamai.wsa.enrichment.domain.model.TimeWindow;
import com.akamai.wsa.enrichment.domain.port.OffenderWindow;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Repository;

import java.time.Duration;
import java.time.Instant;

@Repository
public class RedisOffenderWindow implements OffenderWindow {

    private static final String KEY_PREFIX = "offender:";
    private static final Duration KEY_TTL_BUFFER = Duration.ofMinutes(1);

    private final StringRedisTemplate stringRedisTemplate;

    public RedisOffenderWindow(StringRedisTemplate stringRedisTemplate) {
        this.stringRedisTemplate = stringRedisTemplate;
    }

    @Override
    public void recordEvent(ClientIp clientIp, Instant occurredAt) {
        String key = keyFor(clientIp);
        double score = occurredAt.toEpochMilli();
        // member is unique per occurrence to avoid dedup collisions on identical timestamps
        stringRedisTemplate.opsForZSet().add(key, occurredAt.toEpochMilli() + ":" + System.nanoTime(), score);
        stringRedisTemplate.expire(key, Duration.ofMinutes(10).plus(KEY_TTL_BUFFER));
    }

    @Override
    public long countRecentEventsFromClient(ClientIp clientIp, TimeWindow timeWindow, Instant asOf) {
        String key = keyFor(clientIp);
        double windowStart = asOf.minus(timeWindow.length()).toEpochMilli();
        double asOfScore = asOf.toEpochMilli();
        // prune anything older than the window, then count what remains in range
        stringRedisTemplate.opsForZSet().removeRangeByScore(key, Double.NEGATIVE_INFINITY, windowStart - 1);
        Long count = stringRedisTemplate.opsForZSet().count(key, windowStart, asOfScore);
        return count == null ? 0L : count;
    }

    private String keyFor(ClientIp clientIp) {
        return KEY_PREFIX + clientIp.value();
    }
}
```

> `System.nanoTime()` only disambiguates ZSet members with identical timestamps; it is not used for the window math (which is score-based). Acceptable for the take-home; a monotonic per-key counter is the hardening step.

- [ ] **Step 4: Run — expect PASS** (Docker required).
- [ ] **Step 5: Commit** — `feat(enrichment): add Redis offender-window adapter`

---

### Task 7: Wire beans + Kafka consume→enrich→publish

**Files:**
- Create: `.../infrastructure/config/EnrichmentConfiguration.java`
- Create: `.../application/EnrichmentService.java`
- Create: `.../interfaces/messaging/RawEventListener.java`
- Create: `.../infrastructure/messaging/EnrichedEventPublisher.java`
- Test: `.../test/java/com/akamai/wsa/enrichment/EnrichmentPipelineEmbeddedKafkaTest.java`

**Interfaces:**
- `EnrichmentService.enrich(RawEventMessage rawEventMessage, String correlationId) → EnrichedEventMessage` — pure orchestration over classifier/calculator/offenderWindow/Clock.
- `RawEventListener` — `@KafkaListener(topics="${wsa.topics.raw}")` on `events.raw`, calls the service, hands the result to `EnrichedEventPublisher`.
- `EnrichedEventPublisher.publish(EnrichedEventMessage enrichedEventMessage, String correlationId)` — `KafkaTemplate` to `events.enriched`, key = `configId`.

- [ ] **Step 1: Wiring config** (pure classes → beans)

```java
package com.akamai.wsa.enrichment.infrastructure.config;

import com.akamai.wsa.enrichment.domain.service.*;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Clock;
import java.util.List;

@Configuration
public class EnrichmentConfiguration {

    @Bean
    public Clock clock() {
        return Clock.systemUTC();
    }

    @Bean
    public AttackTypeClassifier attackTypeClassifier() {
        return new DefaultAttackTypeClassifier();
    }

    @Bean
    public ThreatScoreCalculator threatScoreCalculator() {
        return new RuleBasedThreatScoreCalculator(List.of(
                new SeverityRule(), new ActionRule(), new SensitivePathRule(), new RepeatOffenderRule()));
    }
}
```

- [ ] **Step 2: Enrichment service** (record-then-read; flag = count > 5)

```java
package com.akamai.wsa.enrichment.application;

import com.akamai.wsa.contracts.EnrichedEventMessage;
import com.akamai.wsa.contracts.RawEventMessage;
import com.akamai.wsa.enrichment.domain.model.ClientIp;
import com.akamai.wsa.enrichment.domain.model.ThreatScore;
import com.akamai.wsa.enrichment.domain.model.TimeWindow;
import com.akamai.wsa.enrichment.domain.port.OffenderWindow;
import com.akamai.wsa.enrichment.domain.service.AttackTypeClassifier;
import com.akamai.wsa.enrichment.domain.service.ThreatScoreCalculator;
import com.akamai.wsa.enrichment.domain.service.ThreatScoringInputs;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.Instant;

@Service
public class EnrichmentService {

    static final int REPEAT_OFFENDER_THRESHOLD = 5;

    private final Clock clock;
    private final OffenderWindow offenderWindow;
    private final AttackTypeClassifier attackTypeClassifier;
    private final ThreatScoreCalculator threatScoreCalculator;
    private final TimeWindow repeatOffenderWindow;

    public EnrichmentService(
            Clock clock,
            OffenderWindow offenderWindow,
            AttackTypeClassifier attackTypeClassifier,
            ThreatScoreCalculator threatScoreCalculator,
            @Value("${wsa.repeat-offender.window-minutes:10}") long windowMinutes) {
        this.clock = clock;
        this.offenderWindow = offenderWindow;
        this.attackTypeClassifier = attackTypeClassifier;
        this.threatScoreCalculator = threatScoreCalculator;
        this.repeatOffenderWindow = TimeWindow.ofMinutes(windowMinutes);
    }

    public EnrichedEventMessage enrich(RawEventMessage rawEventMessage) {
        Instant receivedAt = Instant.now(clock);
        ClientIp clientIp = new ClientIp(rawEventMessage.clientIp());

        offenderWindow.recordEvent(clientIp, receivedAt);
        long recentCount = offenderWindow.countRecentEventsFromClient(clientIp, repeatOffenderWindow, receivedAt);
        boolean repeatOffender = recentCount > REPEAT_OFFENDER_THRESHOLD;

        ThreatScoringInputs threatScoringInputs = new ThreatScoringInputs(
                rawEventMessage.rule().severity(),
                rawEventMessage.action(),
                rawEventMessage.path(),
                repeatOffender);
        ThreatScore threatScore = threatScoreCalculator.calculate(threatScoringInputs);
        String attackType = attackTypeClassifier.classify(rawEventMessage.rule().category()).displayName();

        return EnrichedEventMessage.from(rawEventMessage, attackType, threatScore.value(), receivedAt);
    }
}
```

> Assumes `EnrichedEventMessage.from(RawEventMessage, String attackType, int threatScore, Instant receivedAt)` exists in `contracts` (add it there if not; a static factory copying raw fields + the three enrichment fields).

- [ ] **Step 3: Kafka listener + publisher**

`RawEventListener.java`
```java
package com.akamai.wsa.enrichment.interfaces.messaging;

import com.akamai.wsa.contracts.EnrichedEventMessage;
import com.akamai.wsa.contracts.MessageEnvelope;
import com.akamai.wsa.contracts.RawEventMessage;
import com.akamai.wsa.enrichment.application.EnrichmentService;
import com.akamai.wsa.enrichment.infrastructure.messaging.EnrichedEventPublisher;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

@Component
public class RawEventListener {

    private final EnrichmentService enrichmentService;
    private final EnrichedEventPublisher enrichedEventPublisher;

    public RawEventListener(EnrichmentService enrichmentService, EnrichedEventPublisher enrichedEventPublisher) {
        this.enrichmentService = enrichmentService;
        this.enrichedEventPublisher = enrichedEventPublisher;
    }

    @KafkaListener(topics = "${wsa.topics.raw}", groupId = "enrichment")
    public void onRawEvent(MessageEnvelope<RawEventMessage> envelope) {
        EnrichedEventMessage enriched = enrichmentService.enrich(envelope.payload());
        enrichedEventPublisher.publish(enriched, envelope.correlationId());
    }
}
```

`EnrichedEventPublisher.java`
```java
package com.akamai.wsa.enrichment.infrastructure.messaging;

import com.akamai.wsa.contracts.EnrichedEventMessage;
import com.akamai.wsa.contracts.MessageEnvelope;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

@Component
public class EnrichedEventPublisher {

    private final KafkaTemplate<String, MessageEnvelope<EnrichedEventMessage>> kafkaTemplate;
    private final String enrichedTopic;

    public EnrichedEventPublisher(
            KafkaTemplate<String, MessageEnvelope<EnrichedEventMessage>> kafkaTemplate,
            @Value("${wsa.topics.enriched}") String enrichedTopic) {
        this.kafkaTemplate = kafkaTemplate;
        this.enrichedTopic = enrichedTopic;
    }

    public void publish(EnrichedEventMessage enrichedEventMessage, String correlationId) {
        String partitionKey = String.valueOf(enrichedEventMessage.configId());
        kafkaTemplate.send(enrichedTopic, partitionKey,
                MessageEnvelope.of(correlationId, enrichedEventMessage));
    }
}
```

- [ ] **Step 4: Write the `@EmbeddedKafka` pipeline test**

Produce a `MessageEnvelope<RawEventMessage>` (CRITICAL/INJECTION/DENY, path `/api/v1/login`) to `events.raw`; consume from `events.enriched`; assert `attackType == "SQL/Command Injection"`, `threatScore == 75`, `receivedAt` present, and `correlationId` preserved. Configure JSON (de)serializers with trusted packages `com.akamai.wsa.contracts`. Use `@EmbeddedKafka(topics = {"events.raw","events.enriched"})` and an in-memory `OffenderWindow` bean (`@TestConfiguration` supplying `InMemoryOffenderWindow`) so Redis is not required.

- [ ] **Step 5: Run the full module suite — expect PASS.** `mvn -q -pl services/enrichment test`

- [ ] **Step 6: `application.yml`** — set `wsa.topics.raw=events.raw`, `wsa.topics.enriched=events.enriched`, `wsa.repeat-offender.window-minutes=10`, Kafka JSON serde trusted packages, consumer `group-id=enrichment`, `server.port=8082`.

- [ ] **Step 7: Commit and tag**

```bash
git commit -m "feat(enrichment): consume raw events, enrich, publish enriched"
git tag v0.3-enrichment
```

---

## Self-Review

- **Spec coverage:** classification ✓ (Task 1); full scoring matrix incl. cap ✓ (Tasks 2–4, worked example 75/90); repeat-offender with `receivedAt` basis, record-then-read, `> 5` (6th event first flagged) ✓ (Tasks 5–7); enriched event published ✓ (Task 7).
- **Purity:** classifier/rules/calculator import nothing from Spring/Kafka/Redis; beans built in `EnrichmentConfiguration`; only adapters + service are annotated.
- **Type consistency:** `ThreatScoringInputs(severity, action, requestPath, repeatOffender)`, `OffenderWindow.recordEvent(...)` + `.countRecentEventsFromClient(clientIp, timeWindow, asOf)`, `ThreatScoreCalculator.calculate(...)` used identically across tasks.
- **Cross-module dependencies flagged:** relies on `contracts` providing `RawEventMessage`, `EnrichedEventMessage` (+ `from(...)` factory), `MessageEnvelope` (`payload()`, `correlationId()`, `of(...)`), and shared enums `AttackCategory`/`Action`/`Severity`/`AttackType`; and on the enrichment module scaffold from the v2 restructure plan. The `OffenderWindow` now carries a `recordEvent` method (a refinement of the earlier count-only port, needed because enrichment doesn't persist events itself).
