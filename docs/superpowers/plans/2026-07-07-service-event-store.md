# event-store Service Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax.

**Goal:** Build the `event-store` service — the enriched-event **system of record**. It consumes `events.enriched` from Kafka and persists each event idempotently, first to an in-memory reference adapter, then to MongoDB behind the same port.

**Architecture:** Hexagonal. A domain `EventStore` port with intention-revealing methods; a `StoredEvent` domain model mapped from the shared `EnrichedEventMessage` contract; adapters (`InMemoryEventStore`, `MongoEventStore`) behind the port; an inbound `@KafkaListener` that maps and persists. Idempotent on `eventId` because Kafka delivery is at-least-once. See `docs/03-sdd.md` v2 §3, §7.

**Tech Stack:** Java 21, Spring Boot 3.3.5, `spring-kafka`, Spring Data MongoDB, Testcontainers (Mongo) + `@EmbeddedKafka`, JUnit 5 + AssertJ.

## Global Constraints

- Base package `com.akamai.wsa.eventstore`. Module `services/event-store` (scaffold already exists: pom with actuator/kafka/data-mongodb, `EventStoreApplication`, `application.yml`).
- The `contracts` module supplies `EnrichedEventMessage`, `MessageEnvelope`, and the shared enums (`AttackCategory`, `Action`, `Severity`).
- **`domain` imports no Spring/Kafka/Mongo types** — only `java.*` and `contracts` enums. Adapters (`infrastructure`) and the listener (`interfaces/messaging`) carry the framework imports.
- **Idempotency:** dedupe on `eventId` (at-least-once delivery must not double-count).
- Records, immutability, full descriptive parameter names (no `v`/`e`/`msg`), conventional commits, single-line logging with `correlationId`. Milestone tag `v0.4-event-store`.
- Topic + Mongo settings live in `application.yml`, never inlined.

---

### Task 1: `StoredEvent` domain + `EventStore` port + in-memory adapter (idempotent)

**Files:**
- Create: `services/event-store/src/main/java/com/akamai/wsa/eventstore/domain/model/StoredEvent.java`
- Create: `.../domain/model/StoredRule.java`, `.../domain/model/StoredGeoLocation.java`
- Create: `.../domain/port/EventStore.java`
- Create: `.../infrastructure/persistence/inmemory/InMemoryEventStore.java`
- Test: `.../test/java/com/akamai/wsa/eventstore/infrastructure/persistence/inmemory/InMemoryEventStoreTest.java`

**Interfaces / Produces:**
- `StoredEvent` — the enriched event this service owns (flat, with nested `StoredRule`/`StoredGeoLocation`), keyed by `eventId`.
- `EventStore` port: `void saveAll(List<StoredEvent> storedEvents)` (idempotent upsert by `eventId`), `long countAll()`, `List<StoredEvent> findByConfigId(int configId)`.

- [ ] **Step 1: Write the failing test**

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
        eventStore.saveAll(List.of(storedEvent("evt-1", 14227))); // redelivery
        assertThat(eventStore.countAll()).isEqualTo(1);
    }
}
```

Add a `StoredEventFixtures.storedEvent(eventId, configId)` test helper in `com.akamai.wsa.eventstore.testsupport` returning a fully-populated `StoredEvent`.

- [ ] **Step 2: Run — expect FAIL.** `mvn -q -pl services/event-store test -Dtest=InMemoryEventStoreTest`

- [ ] **Step 3: Implement the domain model**

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

- [ ] **Step 4: Implement the port**

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

- [ ] **Step 5: Implement the in-memory adapter (dedupe by eventId)**

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

- [ ] **Step 6: Run — expect PASS.** Commit — `feat(event-store): add StoredEvent domain, EventStore port, in-memory adapter`.

---

### Task 2: Map `EnrichedEventMessage` → `StoredEvent`

**Files:**
- Create: `.../application/EnrichedEventMapper.java`
- Test: `.../test/java/com/akamai/wsa/eventstore/application/EnrichedEventMapperTest.java`

**Interfaces:** Consumes `contracts.EnrichedEventMessage`; Produces `StoredEvent`. Pure mapper (no framework).

- [ ] **Step 1: Failing test** — build an `EnrichedEventMessage` (all fields incl. `attackType`, `threatScore`, `receivedAt`), map it, assert every field lands on the `StoredEvent` (including nested rule/geo and enums).

- [ ] **Step 2: Run — FAIL.**

- [ ] **Step 3: Implement**

```java
package com.akamai.wsa.eventstore.application;

import com.akamai.wsa.contracts.EnrichedEventMessage;
import com.akamai.wsa.eventstore.domain.model.StoredEvent;
import com.akamai.wsa.eventstore.domain.model.StoredGeoLocation;
import com.akamai.wsa.eventstore.domain.model.StoredRule;

/** Maps the wire contract to the owned persistence model. Pure. */
public final class EnrichedEventMapper {

    private EnrichedEventMapper() {
    }

    public static StoredEvent toStoredEvent(EnrichedEventMessage enrichedEventMessage) {
        return new StoredEvent(
                enrichedEventMessage.eventId(),
                enrichedEventMessage.timestamp(),
                enrichedEventMessage.configId(),
                enrichedEventMessage.policyId(),
                enrichedEventMessage.clientIp(),
                enrichedEventMessage.hostname(),
                enrichedEventMessage.path(),
                enrichedEventMessage.method(),
                enrichedEventMessage.statusCode(),
                enrichedEventMessage.userAgent(),
                new StoredRule(
                        enrichedEventMessage.rule().id(),
                        enrichedEventMessage.rule().name(),
                        enrichedEventMessage.rule().message(),
                        enrichedEventMessage.rule().severity(),
                        enrichedEventMessage.rule().category()),
                enrichedEventMessage.action(),
                new StoredGeoLocation(
                        enrichedEventMessage.geoLocation().country(),
                        enrichedEventMessage.geoLocation().city()),
                enrichedEventMessage.requestSize(),
                enrichedEventMessage.responseSize(),
                enrichedEventMessage.attackType(),
                enrichedEventMessage.threatScore(),
                enrichedEventMessage.receivedAt());
    }
}
```

> If `EnrichedEventMessage`'s field names/shape differ, adjust the accessors — keep the mapper a straight field copy. Adjust nested-accessor calls if `contracts` models rule/geo as flat fields instead of nested records.

- [ ] **Step 4: Run — PASS.** Commit — `feat(event-store): map enriched contract to StoredEvent`.

---

### Task 3: Kafka listener on `events.enriched` (idempotent persist)

**Files:**
- Create: `.../interfaces/messaging/EnrichedEventListener.java`
- Modify: `services/event-store/src/main/resources/application.yml` (consumer group, topic, JSON deserializer, trusted packages)
- Test: `.../test/java/com/akamai/wsa/eventstore/interfaces/messaging/EnrichedEventListenerIntegrationTest.java` (`@SpringBootTest` + `@EmbeddedKafka`)

**Interfaces:** Consumes the enveloped `EnrichedEventMessage` from `events.enriched`; persists via `EventStore`.

- [ ] **Step 1: Failing integration test** — with `@EmbeddedKafka`, publish a `MessageEnvelope<EnrichedEventMessage>` to `events.enriched`, then await until `EventStore.countAll() == 1`; publish the SAME event again and assert `countAll()` stays `1` (dedupe). Use Awaitility or a polling loop with a bounded timeout.

- [ ] **Step 2: Run — FAIL.**

- [ ] **Step 3: Implement the listener**

```java
package com.akamai.wsa.eventstore.interfaces.messaging;

import com.akamai.wsa.contracts.EnrichedEventMessage;
import com.akamai.wsa.contracts.MessageEnvelope;
import com.akamai.wsa.eventstore.application.EnrichedEventMapper;
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

    @KafkaListener(topics = "${wsa.topics.enriched}", groupId = "${wsa.consumer-group}")
    public void onEnrichedEvent(MessageEnvelope<EnrichedEventMessage> envelope) {
        String correlationId = envelope.correlationId();
        var storedEvent = EnrichedEventMapper.toStoredEvent(envelope.payload());
        eventStore.saveAll(List.of(storedEvent));
        logger.info("EnrichedEventListener - onEnrichedEvent", storedEvent.eventId(), correlationId);
    }
}
```

- [ ] **Step 4:** Add consumer config to `application.yml` (`spring.kafka.consumer` with a `JsonDeserializer`, `spring.json.trusted.packages: com.akamai.wsa.contracts`, `wsa.topics.enriched: events.enriched`, `wsa.consumer-group: event-store`).

- [ ] **Step 5: Run — PASS.** Commit — `feat(event-store): consume events.enriched and persist idempotently`.

---

### Task 4: MongoDB adapter behind the port (Testcontainers)

**Files:**
- Create: `.../infrastructure/persistence/mongo/StoredEventDocument.java` (`@Document("events")`, `@Id String eventId`, `@CompoundIndex` on `(configId, timestamp)` and `(clientIp, timestamp)`)
- Create: `.../infrastructure/persistence/mongo/StoredEventMongoRepository.java` (`extends MongoRepository<StoredEventDocument, String>`, `List<StoredEventDocument> findByConfigId(int configId)`)
- Create: `.../infrastructure/persistence/mongo/MongoEventStore.java` (`implements EventStore`, maps StoredEvent↔document, `@Primary` under a `mongo` profile)
- Create: `.../infrastructure/persistence/mongo/StoredEventDocumentMapper.java`
- Test: `.../test/java/com/akamai/wsa/eventstore/infrastructure/persistence/mongo/MongoEventStoreIT.java` (Testcontainers Mongo)

**Interfaces:** A second `EventStore` implementation. `saveAll` upserts by `_id = eventId` (idempotent). `findByConfigId` is index-backed.

- [ ] **Step 1: Failing Testcontainers integration test** — `@Testcontainers` with `MongoDBContainer`, `@DynamicPropertySource` wiring `spring.data.mongodb.uri`; save events (incl. a duplicate `eventId`), assert `countAll()` dedupes and `findByConfigId` returns the right subset.

- [ ] **Step 2: Run — FAIL.**

- [ ] **Step 3: Implement** the `@Document`, repository, mapper, and `MongoEventStore` (use `_id = eventId` so re-inserting the same event is a no-op upsert; `saveAll` via `repository.saveAll(...)` where `save` on an existing `_id` replaces — idempotent by identity). Declare the two compound indexes on the document.

- [ ] **Step 4: Run — PASS.** Commit — `feat(event-store): add MongoDB adapter with idempotent upsert and indexes`.

---

### Task 5 (follow-on, NOT built here): PostgreSQL load-test adapter

Deferred to the storage load-test phase (milestone `v0.x-postgres-loadtest`). A `PostgresEventStore implements EventStore` (JDBC/JPA) drops in behind the same port — no domain/listener changes. It exists to **benchmark Mongo vs Postgres** on the same workload (SDD v2 §7). Logged here as an explicit follow-on so it isn't forgotten; do not build it in this plan.

---

## Self-Review

- **Spec coverage:** consumes `events.enriched` ✓ (Task 3); persists the enriched event as system of record ✓ (Tasks 1, 4); idempotent on `eventId` for at-least-once delivery ✓ (Tasks 1, 3, 4); in-memory-first then Mongo behind one port ✓; Postgres load-test noted ✓ (Task 5).
- **Placeholder scan:** none — every code step is complete; the mapper carries a note to adjust accessors to the final `contracts` shape.
- **Type consistency:** `EventStore` methods (`saveAll`/`countAll`/`findByConfigId`) identical across port, in-memory, and Mongo adapters and the listener; `StoredEvent`/`StoredRule`/`StoredGeoLocation` used consistently; `MessageEnvelope`/`EnrichedEventMessage` come from `contracts`.
- **Domain purity:** `domain/model` and `domain/port` import only `java.*` + `contracts` enums; Spring/Kafka/Mongo confined to `infrastructure` and `interfaces/messaging`.
- **Shared-schema note:** `analytics` reads a Mongo replica of the `events` collection — the document shape (`StoredEventDocument`) is the shared contract for that read path; keep it stable and documented.
