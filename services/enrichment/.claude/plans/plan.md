# Enrichment Service — Implementation Plan (patched to contracts v1)

> **For agentic workers:** REQUIRED SUB-SKILL: superpowers:subagent-driven-development or superpowers:executing-plans. Steps use `- [ ]` checkboxes.
> Patched 2026-07-07 to match the committed `contracts` API and to add eventId
> dedup (see `reconciliation.md` in this folder). SHARED CONTRACTS: read
> `services/contracts/.claude/context.md` before coding.

**Goal:** The enrichment service consumes `events.raw`, classifies + threat-scores each event (with the repeat-offender bonus), and publishes an `EnrichedEventMessage` to `events.enriched`.

**Architecture:** Pure domain (Logic-2 rule-object scorer, offender flag injected), Kafka listener/publisher as inbound/outbound adapters, in-memory `OffenderWindow` first (Redis deferred). `domain` imports no Spring/Kafka/Redis.

**Tech Stack:** Java 21, Spring Boot 3.3.5, spring-kafka, `contracts`. Base package `com.akamai.wsa.enrichment`. Port 8082. Milestone `v0.3-enrichment`.

## Global Constraints
- Scorer is pure; the repeat-offender flag is computed by the caller and injected via `ThreatScoringInputs`.
- **Locked semantics:** record-then-read; count includes the current event; flag `count > 5` (the **6th** event in the 10-min window is first flagged); window basis = `receivedAt`; window 10 min; weights CRITICAL/HIGH/MEDIUM/LOW = 40/30/20/10, DENY/ALERT/MONITOR = 20/10/0, sensitive path (`/admin`|`/login`) +15, repeat-offender +15, cap 100.
- **Contracts (v1):** `MessageEnvelope.of(correlationId, occurredAt, payload)` is **3-arg**, `version` is `int`. `EnrichedEventMessage` is **nested** — build with `new EnrichedEventMessage(rawEvent, attackTypeDisplayName, threatScore, receivedAt)`; read incoming fields via `rawEvent().…`. There is **no `contracts.AttackType`** (define it enrichment-local) and **no `EnrichedEventMessage.from(...)`**.
- **Idempotency:** dedupe by `eventId` — redelivery must not re-record the offender window or re-publish.
- Full descriptive parameter names; records; conventional commits.

---

### Task 1: `AttackType` (enrichment-local) + classifier
`contracts` has no `AttackType`; the wire form is a String display name, so it lives in the enrichment domain.

**Files:** `domain/model/AttackType.java`, `domain/service/AttackTypeClassifier.java`, `domain/service/DefaultAttackTypeClassifier.java`; test `domain/service/DefaultAttackTypeClassifierTest.java`.

- [ ] **Step 1: failing test**
```java
package com.akamai.wsa.enrichment.domain.service;

import com.akamai.wsa.contracts.AttackCategory;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;

class DefaultAttackTypeClassifierTest {
    private final AttackTypeClassifier classifier = new DefaultAttackTypeClassifier();

    @Test
    void mapsCategoryToDisplayName() {
        assertThat(classifier.displayNameFor(AttackCategory.INJECTION)).isEqualTo("SQL/Command Injection");
        assertThat(classifier.displayNameFor(AttackCategory.RATE_LIMIT)).isEqualTo("Rate Limiting");
    }
}
```
- [ ] **Step 2:** run → FAIL.
- [ ] **Step 3: implement**
```java
package com.akamai.wsa.enrichment.domain.model;

import com.akamai.wsa.contracts.AttackCategory;

public enum AttackType {
    SQL_COMMAND_INJECTION("SQL/Command Injection"),
    CROSS_SITE_SCRIPTING("Cross-Site Scripting"),
    PROTOCOL_ANOMALY("Protocol Anomaly"),
    DATA_EXFILTRATION("Data Exfiltration"),
    BOT_ACTIVITY("Bot Activity"),
    DENIAL_OF_SERVICE("Denial of Service"),
    RATE_LIMITING("Rate Limiting");

    private final String displayName;
    AttackType(String displayName) { this.displayName = displayName; }
    public String displayName() { return displayName; }

    public static AttackType fromCategory(AttackCategory attackCategory) {
        return switch (attackCategory) {
            case INJECTION -> SQL_COMMAND_INJECTION;
            case XSS -> CROSS_SITE_SCRIPTING;
            case PROTOCOL_VIOLATION -> PROTOCOL_ANOMALY;
            case DATA_LEAKAGE -> DATA_EXFILTRATION;
            case BOT -> BOT_ACTIVITY;
            case DOS -> DENIAL_OF_SERVICE;
            case RATE_LIMIT -> RATE_LIMITING;
        };
    }
}
```
```java
package com.akamai.wsa.enrichment.domain.service;

import com.akamai.wsa.contracts.AttackCategory;

public interface AttackTypeClassifier {
    String displayNameFor(AttackCategory attackCategory);
}
```
```java
package com.akamai.wsa.enrichment.domain.service;

import com.akamai.wsa.contracts.AttackCategory;
import com.akamai.wsa.enrichment.domain.model.AttackType;

public final class DefaultAttackTypeClassifier implements AttackTypeClassifier {
    @Override
    public String displayNameFor(AttackCategory attackCategory) {
        return AttackType.fromCategory(attackCategory).displayName();
    }
}
```
- [ ] **Step 4:** run → PASS. **Step 5:** commit `feat: add enrichment attack-type classifier`.

---

### Task 2: `ThreatScore` VO + `ThreatScoringInputs`
**Files:** `domain/model/ThreatScore.java`, `domain/service/ThreatScoringInputs.java`; test `domain/model/ThreatScoreTest.java`.

- [ ] Test caps + range (0..100, `ofCapped`), then implement:
```java
package com.akamai.wsa.enrichment.domain.model;

public record ThreatScore(int value) {
    public static final int MINIMUM = 0;
    public static final int MAXIMUM = 100;
    public ThreatScore { if (value < MINIMUM || value > MAXIMUM) throw new IllegalArgumentException("threatScore out of range"); }
    public static ThreatScore ofCapped(int rawScore) { return new ThreatScore(Math.max(MINIMUM, Math.min(MAXIMUM, rawScore))); }
}
```
```java
package com.akamai.wsa.enrichment.domain.service;

import com.akamai.wsa.contracts.Action;
import com.akamai.wsa.contracts.Severity;

public record ThreatScoringInputs(Severity severity, Action action, String requestPath, boolean repeatOffender) {
    public ThreatScoringInputs {
        if (severity == null || action == null || requestPath == null)
            throw new IllegalArgumentException("severity, action, requestPath must not be null");
    }
}
```
- [ ] commit `feat: add threat-score value object and scoring inputs`.

---

### Task 3: Composable scoring rules
**Files:** `domain/service/ScoringRule.java` + `SeverityRule`/`ActionRule`/`SensitivePathRule`/`RepeatOffenderRule`; test `ScoringRulesTest.java`.

- [ ] Test each rule's points, then implement (weights as constants):
```java
package com.akamai.wsa.enrichment.domain.service;
public interface ScoringRule { int points(ThreatScoringInputs threatScoringInputs); }
```
```java
public final class SeverityRule implements ScoringRule {
    public static final int CRITICAL_POINTS = 40, HIGH_POINTS = 30, MEDIUM_POINTS = 20, LOW_POINTS = 10;
    public int points(ThreatScoringInputs in) {
        return switch (in.severity()) { case CRITICAL -> CRITICAL_POINTS; case HIGH -> HIGH_POINTS; case MEDIUM -> MEDIUM_POINTS; case LOW -> LOW_POINTS; };
    }
}
```
```java
public final class ActionRule implements ScoringRule {
    public static final int DENY_POINTS = 20, ALERT_POINTS = 10, MONITOR_POINTS = 0;
    public int points(ThreatScoringInputs in) {
        return switch (in.action()) { case DENY -> DENY_POINTS; case ALERT -> ALERT_POINTS; case MONITOR -> MONITOR_POINTS; };
    }
}
```
```java
public final class SensitivePathRule implements ScoringRule {
    public static final int SENSITIVE_PATH_POINTS = 15;
    public int points(ThreatScoringInputs in) {
        String p = in.requestPath();
        return (p.contains("/admin") || p.contains("/login")) ? SENSITIVE_PATH_POINTS : 0;
    }
}
```
```java
public final class RepeatOffenderRule implements ScoringRule {
    public static final int REPEAT_OFFENDER_POINTS = 15;
    public int points(ThreatScoringInputs in) { return in.repeatOffender() ? REPEAT_OFFENDER_POINTS : 0; }
}
```
- [ ] commit `feat: add composable threat-scoring rules`.

---

### Task 4: `RuleBasedThreatScoreCalculator` (graded matrix)
**Files:** `domain/service/ThreatScoreCalculator.java`, `RuleBasedThreatScoreCalculator.java`; test `RuleBasedThreatScoreCalculatorTest.java`.

- [ ] Exhaustive test: worked example CRITICAL+DENY+`/login`+no-repeat = 75; +repeat = 90; every severity tier; MONITOR/non-sensitive minimal = 10. Then:
```java
package com.akamai.wsa.enrichment.domain.service;
import com.akamai.wsa.enrichment.domain.model.ThreatScore;
public interface ThreatScoreCalculator { ThreatScore calculate(ThreatScoringInputs threatScoringInputs); }
```
```java
package com.akamai.wsa.enrichment.domain.service;
import com.akamai.wsa.enrichment.domain.model.ThreatScore;
import java.util.List;

public final class RuleBasedThreatScoreCalculator implements ThreatScoreCalculator {
    private final List<ScoringRule> scoringRules;
    public RuleBasedThreatScoreCalculator(List<ScoringRule> scoringRules) { this.scoringRules = List.copyOf(scoringRules); }
    public ThreatScore calculate(ThreatScoringInputs threatScoringInputs) {
        int total = scoringRules.stream().mapToInt(rule -> rule.points(threatScoringInputs)).sum();
        return ThreatScore.ofCapped(total);
    }
}
```
- [ ] commit `feat: add rule-based threat-score calculator`.

---

### Task 5: `OffenderWindow` port + in-memory adapter (record-then-read)
**Files:** `domain/port/OffenderWindow.java`, `infrastructure/window/InMemoryOffenderWindow.java`; test `InMemoryOffenderWindowTest.java`.

- [ ] Test: recording the same IP N times then counting within the window returns N; events outside the 10-min window excluded; boundary at exactly 10 min included. Then:
```java
package com.akamai.wsa.enrichment.domain.port;
import java.time.Duration;
import java.time.Instant;

public interface OffenderWindow {
    void recordEvent(String clientIp, Instant receivedAt);
    long countRecentEventsFromClient(String clientIp, Duration window, Instant asOf);
}
```
```java
package com.akamai.wsa.enrichment.infrastructure.window;

import com.akamai.wsa.enrichment.domain.port.OffenderWindow;
import org.springframework.stereotype.Component;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

@Component
public class InMemoryOffenderWindow implements OffenderWindow {
    private final Map<String, List<Instant>> eventsByClient = new ConcurrentHashMap<>();

    @Override
    public void recordEvent(String clientIp, Instant receivedAt) {
        eventsByClient.computeIfAbsent(clientIp, ignored -> new CopyOnWriteArrayList<>()).add(receivedAt);
    }

    @Override
    public long countRecentEventsFromClient(String clientIp, Duration window, Instant asOf) {
        Instant windowStart = asOf.minus(window);
        return eventsByClient.getOrDefault(clientIp, List.of()).stream()
                .filter(receivedAt -> !receivedAt.isBefore(windowStart) && !receivedAt.isAfter(asOf))
                .count();
    }
}
```
> Note (README): the in-memory window is **per-instance** — correct for single-instance dry runs only. Horizontal scaling needs the Redis window (Task 6) *and* `events.raw` partitioned by `clientIp` so one IP maps to one consumer.
- [ ] commit `feat: add in-memory repeat-offender window`.

---

### Task 6 (DEFERRED — hardening): `RedisOffenderWindow`
Redis sorted-set adapter (`ZADD`, `ZREMRANGEBYSCORE`, `ZCOUNT`, TTL) behind the same port, Testcontainers test. **Deferred to the DB-candidate phase** per the in-memory-first directive — do not build until the in-memory pipeline is green end-to-end.

---

### Task 7: Dedup + wiring (config → service → listener → publisher)
**Files:** `infrastructure/config/EnrichmentConfiguration.java`; `application/EnrichmentService.java`; `domain/port/ProcessedEventLog.java` + `infrastructure/dedup/InMemoryProcessedEventLog.java`; `interfaces/messaging/RawEventListener.java`; `infrastructure/messaging/EnrichedEventPublisher.java`; tests `EnrichmentServiceTest.java`, `RawEventListenerKafkaTest.java` (`@EmbeddedKafka`).

- [ ] **Dedup port** (idempotency by eventId):
```java
package com.akamai.wsa.enrichment.domain.port;
public interface ProcessedEventLog {
    /** @return true if this eventId was newly recorded; false if already seen. */
    boolean markProcessed(String eventId);
}
```
```java
package com.akamai.wsa.enrichment.infrastructure.dedup;
import com.akamai.wsa.enrichment.domain.port.ProcessedEventLog;
import org.springframework.stereotype.Component;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentHashMap.KeySetView;

@Component
public class InMemoryProcessedEventLog implements ProcessedEventLog {
    private final KeySetView<String, Boolean> seen = ConcurrentHashMap.newKeySet();
    @Override public boolean markProcessed(String eventId) { return seen.add(eventId); }
}
```

- [ ] **EnrichmentService** — pure orchestration; returns the enriched payload (or empty if duplicate):
```java
package com.akamai.wsa.enrichment.application;

import com.akamai.wsa.contracts.EnrichedEventMessage;
import com.akamai.wsa.contracts.RawEventMessage;
import com.akamai.wsa.enrichment.domain.port.OffenderWindow;
import com.akamai.wsa.enrichment.domain.port.ProcessedEventLog;
import com.akamai.wsa.enrichment.domain.service.AttackTypeClassifier;
import com.akamai.wsa.enrichment.domain.service.ThreatScoreCalculator;
import com.akamai.wsa.enrichment.domain.service.ThreatScoringInputs;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Optional;

@Service
public class EnrichmentService {
    static final Duration REPEAT_OFFENDER_WINDOW = Duration.ofMinutes(10);
    static final int REPEAT_OFFENDER_THRESHOLD = 5;

    private final Clock clock;
    private final ProcessedEventLog processedEventLog;
    private final OffenderWindow offenderWindow;
    private final AttackTypeClassifier attackTypeClassifier;
    private final ThreatScoreCalculator threatScoreCalculator;

    public EnrichmentService(Clock clock, ProcessedEventLog processedEventLog, OffenderWindow offenderWindow,
                             AttackTypeClassifier attackTypeClassifier, ThreatScoreCalculator threatScoreCalculator) {
        this.clock = clock;
        this.processedEventLog = processedEventLog;
        this.offenderWindow = offenderWindow;
        this.attackTypeClassifier = attackTypeClassifier;
        this.threatScoreCalculator = threatScoreCalculator;
    }

    /** Empty when the event was already processed (idempotent on redelivery). */
    public Optional<EnrichedEventMessage> enrich(RawEventMessage rawEvent) {
        if (!processedEventLog.markProcessed(rawEvent.eventId())) {
            return Optional.empty();
        }
        Instant receivedAt = clock.instant();
        offenderWindow.recordEvent(rawEvent.clientIp(), receivedAt);
        long recentCount = offenderWindow.countRecentEventsFromClient(rawEvent.clientIp(), REPEAT_OFFENDER_WINDOW, receivedAt);
        boolean repeatOffender = recentCount > REPEAT_OFFENDER_THRESHOLD;

        ThreatScoringInputs inputs = new ThreatScoringInputs(
                rawEvent.rule().severity(), rawEvent.action(), rawEvent.path(), repeatOffender);
        int threatScore = threatScoreCalculator.calculate(inputs).value();
        String attackType = attackTypeClassifier.displayNameFor(rawEvent.rule().category());

        return Optional.of(new EnrichedEventMessage(rawEvent, attackType, threatScore, receivedAt));
    }
}
```

- [ ] **Publisher** — 3-arg `MessageEnvelope.of`, key on `rawEvent().configId()`:
```java
package com.akamai.wsa.enrichment.infrastructure.messaging;

import com.akamai.wsa.contracts.EnrichedEventMessage;
import com.akamai.wsa.contracts.MessageEnvelope;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

import java.time.Instant;

@Component
public class EnrichedEventPublisher {
    private final KafkaTemplate<String, MessageEnvelope<EnrichedEventMessage>> kafkaTemplate;
    private final String enrichedTopic;

    public EnrichedEventPublisher(KafkaTemplate<String, MessageEnvelope<EnrichedEventMessage>> kafkaTemplate,
                                  @Value("${wsa.topics.enriched:events.enriched}") String enrichedTopic) {
        this.kafkaTemplate = kafkaTemplate;
        this.enrichedTopic = enrichedTopic;
    }

    public void publish(String correlationId, EnrichedEventMessage enrichedEvent) {
        MessageEnvelope<EnrichedEventMessage> envelope =
                MessageEnvelope.of(correlationId, Instant.now(), enrichedEvent); // 3-arg factory
        String partitionKey = String.valueOf(enrichedEvent.rawEvent().configId());
        kafkaTemplate.send(enrichedTopic, partitionKey, envelope);
    }
}
```
> Note: if a deterministic `occurredAt` is wanted, pass `enrichedEvent.receivedAt()` instead of `Instant.now()`.

- [ ] **Listener** — consume `events.raw`, dedup + enrich + publish, preserve correlationId:
```java
package com.akamai.wsa.enrichment.interfaces.messaging;

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

    @KafkaListener(topics = "${wsa.topics.raw:events.raw}", groupId = "enrichment")
    public void onRawEvent(MessageEnvelope<RawEventMessage> envelope) {
        enrichmentService.enrich(envelope.payload())
                .ifPresent(enriched -> enrichedEventPublisher.publish(envelope.correlationId(), enriched));
    }
}
```

- [ ] **Config** — wire the pure beans + a `Clock`:
```java
package com.akamai.wsa.enrichment.infrastructure.config;

import com.akamai.wsa.enrichment.domain.service.*;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import java.time.Clock;
import java.util.List;

@Configuration
public class EnrichmentConfiguration {
    @Bean Clock clock() { return Clock.systemUTC(); }
    @Bean AttackTypeClassifier attackTypeClassifier() { return new DefaultAttackTypeClassifier(); }
    @Bean ThreatScoreCalculator threatScoreCalculator() {
        return new RuleBasedThreatScoreCalculator(List.of(
                new SeverityRule(), new ActionRule(), new SensitivePathRule(), new RepeatOffenderRule()));
    }
}
```

- [ ] **Tests:**
  - `EnrichmentServiceTest` (fixed `Clock`): single event → attackType `"SQL/Command Injection"`, score 75, receivedAt stamped; 6th event from one IP within window → score 90; **duplicate eventId → `Optional.empty()` and offender count not inflated**.
  - `RawEventListenerKafkaTest` (`@EmbeddedKafka`): publish a `MessageEnvelope<RawEventMessage>` to `events.raw` → assert a well-formed `MessageEnvelope<EnrichedEventMessage>` appears on `events.enriched` with correlationId preserved and `attackType`/`threatScore` set. Configure JSON (de)serializers for the enveloped generic types (use `JsonDeserializer` with the parametric type / trusted packages `com.akamai.wsa.*`).
  - Kafka JSON serialization config lives in `application.yml` (`spring.kafka.consumer/producer` value serializers) — bind topic names from `wsa.topics.*`.

- [ ] **Verify + commit + tag:** `mvn -q -pl services/enrichment test` green; `git commit -m "feat: enrich events via Kafka with dedup and repeat-offender scoring"`; `git tag v0.3-enrichment`.

---

## Self-Review
- Contracts touchpoints all corrected: enrichment-local `AttackType` (String display name on wire); 3-arg `MessageEnvelope.of`; `EnrichedEventMessage` built via constructor; partition key via `rawEvent().configId()`.
- eventId dedup added (`ProcessedEventLog`) so at-least-once redelivery neither inflates the window nor duplicates output.
- Scoring + offender semantics unchanged (they already matched SDD §6). Redis + DLT explicitly deferred to hardening, in-memory-first per directive.
