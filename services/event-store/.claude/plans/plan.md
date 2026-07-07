# event-store Service Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: superpowers:subagent-driven-development or superpowers:executing-plans. Steps use checkbox (`- [ ]`) syntax.
> **Patched 2026-07-07** from `reconciliation.md`: mapper now reads the **nested** `EnrichedEventMessage.rawEvent()`; generic Kafka deserialization resolved explicitly; Mongo/Postgres **deferred to the DB-candidate phase** (in-memory-first for dry runs); analytics-read indexes + dead-letter noted.

**Goal:** Build `event-store` — the enriched-event **system of record**. It consumes `events.enriched`, maps the contract to its owned model, and persists idempotently. Dry runs use the in-memory adapter; MongoDB (and later a Postgres load-test adapter) drop in behind the same port in the DB-candidate phase.

**Architecture:** Hexagonal. Domain `EventStore` port + `StoredEvent` model mapped from `contracts.EnrichedEventMessage`; adapters behind the port; an inbound `@KafkaListener`. Idempotent on `eventId` (Kafka is at-least-once). SDD v2 §3, §5.1, §7, §14.

**Tech Stack:** Java 21, Spring Boot 3.3.5, `spring-kafka`, JUnit 5 + AssertJ, `@EmbeddedKafka`; (DB phase) Spring Data MongoDB + Testcontainers.

## Global Constraints

- Base package `com.akamai.wsa.eventstore`. Module `services/event-store`.
- **Scaffold reality:** the pom currently has only `web`/`actuator`/`contracts`/`test` (a `/v1/ping` scaffold). The `spring-kafka` starter is added in **Task 3**; the `spring-data-mongodb` starter in the **deferred** Mongo task — not before.
- `contracts` supplies `EnrichedEventMessage`, `RawEventMessage`, `MessageEnvelope`, and enums `AttackCategory`/`Action`/`Severity`. **Read `services/contracts/.claude/context.md` first** — `EnrichedEventMessage` is NESTED (`.rawEvent()`), `attackType` is a `String`, there is no `contracts.AttackType`.
- **`domain` imports no Spring/Kafka/Mongo** — only `java.*` + `contracts` enums.
- **Idempotency:** dedupe on `eventId`.
- Records, immutability, full parameter names, conventional commits, single-line logging with `correlationId`. Milestone `v0.4-event-store`.

---

### Task 1: `StoredEvent` domain + `EventStore` port + in-memory adapter (idempotent) — **the dry-run store**

**Files:**
- Create: `.../domain/model/StoredEvent.java`, `.../domain/model/StoredRule.java`, `.../domain/model/StoredGeoLocation.java`
- Create: `.../domain/port/EventStore.java`
- Create: `.../infrastructure/persistence/inmemory/InMemoryEventStore.java`
- Test: `.../test/java/.../infrastructure/persistence/inmemory/InMemoryEventStoreTest.java`
- Test helper: `.../test/java/.../testsupport/StoredEventFixtures.java`

**Produces:** `StoredEvent` (owned model, keyed by `eventId`); `EventStore` port — `void saveAll(List<StoredEvent>)` (idempotent upsert by eventId), `long countAll()`, `List<StoredEvent> findByConfigId(int)`.

- [ ] **Step 1: Failing test**
```java
package com.akamai.wsa.eventstore.infrastructure.persistence.inmemory;

import com.akamai.wsa.eventstore.domain.model.StoredEvent;
import org.junit.jupiter.api.Test;

import java.util.List;

import static com.akamai.wsa.eventstore.testsupport.StoredEventFixtures.storedEvent;
import static org.assertj.core.api.Assertions.assertThat;

class InMemoryEventStoreTest {

    private final InMemoryEventStore eventStore = new InMemoryEventStore();

    @Test
    void savesAndCountsEvents() {
        eventStore.saveAll(List.of(storedEvent("evt-1", 14227), storedEvent("evt-2", 14227)));
        assertThat(eventStore.countAll()).isEqualTo(2);
    }

    @Test
    void findsByConfigId() {
        eventStore.saveAll(List.of(storedEvent("evt-1", 14227), storedEvent("evt-2", 99999)));
        assertThat(eventStore.findByConfigId(14227)).extracting(StoredEvent::eventId).containsExactly("evt-1");
    }

    @Test
    void deduplicatesByEventIdOnRedelivery() {
        eventStore.saveAll(List.of(storedEvent("evt-1", 14227)));
        eventStore.saveAll(List.of(storedEvent("evt-1", 14227)));
        assertThat(eventStore.countAll()).isEqualTo(1);
    }
}
```
`StoredEventFixtures.storedEvent(eventId, configId)` returns a fully-populated `StoredEvent` (CRITICAL/INJECTION/DENY, a clientIp, `/api/v1/login`, attackType `"SQL/Command Injection"`, threatScore 75, timestamps).

- [ ] **Step 2: Run — FAIL.** `mvn -q -pl services/event-store test -Dtest=InMemoryEventStoreTest`

- [ ] **Step 3: Domain model** (unchanged from the pre-patch plan — imports `contracts` enums only)
```java
package com.akamai.wsa.eventstore.domain.model;

import com.akamai.wsa.contracts.AttackCategory;
import com.akamai.wsa.contracts.Severity;

public record StoredRule(String id, String name, String message, Severity severity, AttackCategory category) {
}
```
```java
package com.akamai.wsa.eventstore.domain.model;

public record StoredGeoLocation(String country, String city) {
}
```
```java
package com.akamai.wsa.eventstore.domain.model;

import com.akamai.wsa.contracts.Action;

import java.time.Instant;

/** The enriched event as owned by event-store (system of record). Keyed by eventId. */
public record StoredEvent(
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
        StoredRule rule,
        Action action,
        StoredGeoLocation geoLocation,
        long requestSize,
        long responseSize,
        String attackType,
        int threatScore,
        Instant receivedAt
) {
    public StoredEvent {
        if (eventId == null || eventId.isBlank()) {
            throw new IllegalArgumentException("eventId must not be blank");
        }
    }
}
```

- [ ] **Step 4: Port**
```java
package com.akamai.wsa.eventstore.domain.port;

import com.akamai.wsa.eventstore.domain.model.StoredEvent;

import java.util.List;

/** Outbound port for the enriched-event system of record. Saves are idempotent on eventId. */
public interface EventStore {
    void saveAll(List<StoredEvent> storedEvents);
    long countAll();
    List<StoredEvent> findByConfigId(int configId);
}
```

- [ ] **Step 5: In-memory adapter (dedupe by eventId)**
```java
package com.akamai.wsa.eventstore.infrastructure.persistence.inmemory;

import com.akamai.wsa.eventstore.domain.model.StoredEvent;
import com.akamai.wsa.eventstore.domain.port.EventStore;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Repository
public class InMemoryEventStore implements EventStore {

    private final Map<String, StoredEvent> eventsById = new ConcurrentHashMap<>();

    @Override
    public void saveAll(List<StoredEvent> storedEvents) {
        for (StoredEvent storedEvent : storedEvents) {
            eventsById.putIfAbsent(storedEvent.eventId(), storedEvent);
        }
    }

    @Override
    public long countAll() {
        return eventsById.size();
    }

    @Override
    public List<StoredEvent> findByConfigId(int configId) {
        return eventsById.values().stream()
                .filter(storedEvent -> storedEvent.configId() == configId)
                .toList();
    }
}
```

- [ ] **Step 6: Run — PASS.** Commit — `feat(event-store): add StoredEvent domain, EventStore port, in-memory adapter`.

---

### Task 2: Map `EnrichedEventMessage` → `StoredEvent`  ⚠️ NESTED contract

**Files:** Create `.../application/EnrichedEventMapper.java`; Test `.../application/EnrichedEventMapperTest.java`.

**PATCH:** `EnrichedEventMessage` is `(RawEventMessage rawEvent, String attackType, int threatScore, Instant receivedAt)` — the original DLR fields live under `.rawEvent()`. Read them there; there is no `.eventId()`/`.configId()`/`.rule()` directly on the enriched message and no `from(...)` factory.

- [ ] **Step 1: Failing test** — build an `EnrichedEventMessage` via its canonical constructor (a `RawEventMessage` + `"SQL/Command Injection"` + `75` + a `receivedAt`), map it, assert every field lands on the `StoredEvent` incl. nested rule/geo and enums.

- [ ] **Step 2: Run — FAIL.**

- [ ] **Step 3: Implement (nested-aware)**
```java
package com.akamai.wsa.eventstore.application;

import com.akamai.wsa.contracts.EnrichedEventMessage;
import com.akamai.wsa.contracts.RawEventMessage;
import com.akamai.wsa.eventstore.domain.model.StoredEvent;
import com.akamai.wsa.eventstore.domain.model.StoredGeoLocation;
import com.akamai.wsa.eventstore.domain.model.StoredRule;

/** Maps the wire contract to the owned persistence model. Pure. */
public final class EnrichedEventMapper {

    private EnrichedEventMapper() {
    }

    public static StoredEvent toStoredEvent(EnrichedEventMessage enrichedEventMessage) {
        RawEventMessage rawEvent = enrichedEventMessage.rawEvent();
        return new StoredEvent(
                rawEvent.eventId(),
                rawEvent.timestamp(),
                rawEvent.configId(),
                rawEvent.policyId(),
                rawEvent.clientIp(),
                rawEvent.hostname(),
                rawEvent.path(),
                rawEvent.method(),
                rawEvent.statusCode(),
                rawEvent.userAgent(),
                new StoredRule(
                        rawEvent.rule().id(),
                        rawEvent.rule().name(),
                        rawEvent.rule().message(),
                        rawEvent.rule().severity(),
                        rawEvent.rule().category()),
                rawEvent.action(),
                new StoredGeoLocation(
                        rawEvent.geoLocation().country(),
                        rawEvent.geoLocation().city()),
                rawEvent.requestSize(),
                rawEvent.responseSize(),
                enrichedEventMessage.attackType(),
                enrichedEventMessage.threatScore(),
                enrichedEventMessage.receivedAt());
    }
}
```

- [ ] **Step 4: Run — PASS.** Commit — `feat(event-store): map enriched contract to StoredEvent`.

---

### Task 3: Kafka listener on `events.enriched` (idempotent persist) — completes the dry-run pipeline

**Files:**
- Modify: `services/event-store/pom.xml` (add `spring-kafka`)
- Create: `.../infrastructure/config/KafkaConsumerConfig.java` (explicit parametric deserializer — the generic fix)
- Create: `.../interfaces/messaging/EnrichedEventListener.java`
- Modify: `.../resources/application.yml` (consumer group, topic, trusted packages)
- Test: `.../test/java/.../interfaces/messaging/EnrichedEventListenerIntegrationTest.java` (`@SpringBootTest` + `@EmbeddedKafka`)

**PATCH — generic deserialization:** a bare `JsonDeserializer` erases the envelope's type parameter and delivers `MessageEnvelope<LinkedHashMap>`. Configure the consumer factory with a `JsonDeserializer` whose target is the **parametric** `MessageEnvelope<EnrichedEventMessage>` and disable type headers so the configured type wins.

- [ ] **Step 1: Failing integration test** — `@EmbeddedKafka`, publish a `MessageEnvelope<EnrichedEventMessage>` to `events.enriched`, poll (Awaitility/bounded loop) until `EventStore.countAll() == 1`; publish the SAME event again, assert `countAll()` stays `1`.

- [ ] **Step 2: Run — FAIL.**

- [ ] **Step 3: Add `spring-kafka` to the pom.**

- [ ] **Step 4: Consumer config (the parametric deserializer)**
```java
package com.akamai.wsa.eventstore.infrastructure.config;

import com.akamai.wsa.contracts.EnrichedEventMessage;
import com.akamai.wsa.contracts.MessageEnvelope;
import com.fasterxml.jackson.databind.JavaType;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.springframework.boot.autoconfigure.kafka.KafkaProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.ConcurrentKafkaListenerContainerFactory;
import org.springframework.kafka.core.ConsumerFactory;
import org.springframework.kafka.core.DefaultKafkaConsumerFactory;
import org.springframework.kafka.support.serializer.ErrorHandlingDeserializer;
import org.springframework.kafka.support.serializer.JsonDeserializer;

@Configuration
public class KafkaConsumerConfig {

    @Bean
    public ConsumerFactory<String, MessageEnvelope<EnrichedEventMessage>> enrichedConsumerFactory(
            KafkaProperties kafkaProperties) {
        ObjectMapper objectMapper = new ObjectMapper().registerModule(new JavaTimeModule());
        JavaType envelopeType = objectMapper.getTypeFactory()
                .constructParametricType(MessageEnvelope.class, EnrichedEventMessage.class);
        JsonDeserializer<MessageEnvelope<EnrichedEventMessage>> valueDeserializer =
                new JsonDeserializer<>(envelopeType, objectMapper);
        valueDeserializer.setUseTypeHeaders(false);
        valueDeserializer.addTrustedPackages("com.akamai.wsa.contracts");
        return new DefaultKafkaConsumerFactory<>(
                kafkaProperties.buildConsumerProperties(null),
                new StringDeserializer(),
                new ErrorHandlingDeserializer<>(valueDeserializer));
    }

    @Bean
    public ConcurrentKafkaListenerContainerFactory<String, MessageEnvelope<EnrichedEventMessage>>
            enrichedListenerContainerFactory(
                    ConsumerFactory<String, MessageEnvelope<EnrichedEventMessage>> enrichedConsumerFactory) {
        var factory = new ConcurrentKafkaListenerContainerFactory<String, MessageEnvelope<EnrichedEventMessage>>();
        factory.setConsumerFactory(enrichedConsumerFactory);
        // Dead-letter: wrap with a DefaultErrorHandler + DeadLetterPublishingRecoverer in hardening.
        return factory;
    }
}
```

- [ ] **Step 5: Listener**
```java
package com.akamai.wsa.eventstore.interfaces.messaging;

import com.akamai.wsa.contracts.EnrichedEventMessage;
import com.akamai.wsa.contracts.MessageEnvelope;
import com.akamai.wsa.eventstore.application.EnrichedEventMapper;
import com.akamai.wsa.eventstore.domain.model.StoredEvent;
import com.akamai.wsa.eventstore.domain.port.EventStore;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class EnrichedEventListener {

    private static final Logger logger = LoggerFactory.getLogger(EnrichedEventListener.class);

    private final EventStore eventStore;

    public EnrichedEventListener(EventStore eventStore) {
        this.eventStore = eventStore;
    }

    @KafkaListener(
            topics = "${wsa.topics.enriched}",
            groupId = "${wsa.consumer-group}",
            containerFactory = "enrichedListenerContainerFactory")
    public void onEnrichedEvent(MessageEnvelope<EnrichedEventMessage> enrichedEventEnvelope) {
        StoredEvent storedEvent = EnrichedEventMapper.toStoredEvent(enrichedEventEnvelope.payload());
        eventStore.saveAll(List.of(storedEvent));
        logger.info("EnrichedEventListener - onEnrichedEvent", storedEvent.eventId(), enrichedEventEnvelope.correlationId());
    }
}
```

- [ ] **Step 6:** `application.yml` — `wsa.topics.enriched: events.enriched`, `wsa.consumer-group: event-store`, and `spring.kafka.bootstrap-servers`.

- [ ] **Step 7: Run — PASS.** Then the **dry-run milestone:** with the in-memory store, verify the end-to-end pipeline (gateway → enrichment → event-store) persists a posted event. Commit — `feat(event-store): consume events.enriched and persist idempotently`; tag `v0.4-event-store`.

---

### Task 4 (DEFERRED — DB-candidate phase): MongoDB adapter behind the port

> Per the standing "cached memory for now, DB candidates once the logic is perfected" directive, this is **not built during the dry-run pipeline**. Build it when we enter the storage load-test phase. It drops in behind `EventStore` with no domain/listener change.

Spec for when we do:
- `.../infrastructure/persistence/mongo/StoredEventDocument.java` — `@Document("events")`, `@Id String eventId` (idempotent upsert by identity), and **compound indexes** serving BOTH this service and the **analytics read replica**: `(configId, timestamp)`, `(clientIp, timestamp)`, and — coordinate with the analytics plan — an index supporting analytics' `category`/`action` filters + `timestamp desc` sort (e.g. `(configId, category, timestamp)` / `(configId, action, timestamp)`). Keep the document shape stable and documented (shared-replica schema).
- Repository (`MongoRepository<StoredEventDocument, String>`, `findByConfigId`), a `StoredEventDocumentMapper`, and `MongoEventStore implements EventStore` marked `@Primary` under a `mongo` Spring profile (so in-memory stays the default runnable bean).
- Testcontainers `MongoDBContainer` IT: save incl. a duplicate `eventId`, assert dedupe + `findByConfigId`.
- Add the `spring-data-mongodb` starter to the pom in this task, not before.
- Add a **dead-letter** path (`DefaultErrorHandler` + `DeadLetterPublishingRecoverer`) for poison messages (SDD §9).

### Task 5 (DEFERRED — load-test phase): PostgreSQL adapter
`PostgresEventStore implements EventStore` (JDBC/JPA) behind the same port, to **benchmark Mongo vs Postgres** on the same workload (SDD v2 §7, milestone `v0.x-postgres-loadtest`). No domain/listener changes.

---

## Self-Review
- **Spec coverage:** consumes `events.enriched` ✓ (Task 3); persists the enriched event as system of record ✓ (Task 1); idempotent on `eventId` ✓ (Tasks 1, 3); in-memory-first dry run ✓; Mongo/Postgres deferred to DB phase ✓ (Tasks 4–5).
- **Contract correctness:** mapper reads the **nested** `EnrichedEventMessage.rawEvent()`; generic envelope deserialization pinned to `MessageEnvelope<EnrichedEventMessage>` via a parametric `JsonDeserializer` (Task 3 Step 4). No `contracts.AttackType`/flat-accessor assumptions remain.
- **Type consistency:** `EventStore` methods identical across port/adapter/listener; `StoredEvent` fields consistent; contracts types per `services/contracts/.claude/context.md`.
- **Domain purity:** `domain/*` imports only `java.*` + `contracts` enums; Spring/Kafka in `infrastructure`/`interfaces`.
- **Analytics coupling:** the `events` document/index shape is the shared read-replica contract — indexes coordinated with the analytics plan; kept stable + documented.
