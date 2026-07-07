# event-generator — Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: superpowers:subagent-driven-development or superpowers:executing-plans. Steps use checkbox (`- [ ]`) syntax.
>
> **SHARED CONTRACTS:** the generated event JSON must mirror the gateway's ingest
> request shape, which mirrors `contracts.RawEventMessage`. Read
> `services/contracts/.claude/context.md` before implementing; contract changes
> are logged in `services/contracts/.claude/CHANGELOG.md` and require re-checking
> this plan.

**Goal:** A standalone `event-generator` CLI service that produces realistic, seed-deterministic security events — including attack waves from a single IP hitting a single path — and either writes them as JSON or POSTs them in batches to the **gateway's** ingestion API.

**Architecture:** A Spring Boot module in the mono-repo (`services/event-generator`, package `com.akamai.wsa.generator`). Generation is pure and seeded (`java.util.Random` + a fixed base timestamp), fully testable without a clock or a live server. Output is split behind small units: a `JsonEventWriter` (file/stdout) and an `IngestionFeeder` driving an `IngestionClient` port (real `RestClient` impl; faked in tests). A `CommandLineRunner` wires config → generate → output.

**Tech Stack:** Java 21, Spring Boot 3.3.5, Jackson (records + JavaTimeModule), JUnit 5 + AssertJ.

## Global Constraints

- **Java 21**; `maven.compiler.release=21` (inherited from `mini-wsa-parent`).
- **Base package:** `com.akamai.wsa.generator`.
- **Module version:** `0.1.0-SNAPSHOT`; parent = `mini-wsa-parent` (`../../pom.xml`).
- **Determinism:** all randomness flows from an injected `java.util.Random` seeded from config; timestamps derive from a configured base `Instant` + offsets. NEVER use `Instant.now()`, `Math.random()`, or an unseeded `Random`. Same seed → identical events.
- **Immutability:** events and config are `record`s; no mutation of inputs.
- **Naming:** intention-revealing; FULL descriptive parameter names (no `v`, `e`, `idx` — use `index`, `random`, `securityEvent`).
- **Ingest target:** the **gateway** runs locally on **8081**; default target URL `http://localhost:8081/v1/events/ingest`. A *full-pipeline* dry run (verifying persistence/analytics) also needs **enrichment + event-store + Kafka** up via docker-compose; a generator↔gateway-only run validates **ingestion acceptance** (`201`) only, since the gateway publishes to Kafka rather than storing.
- **Event JSON shape:** must mirror the gateway's `IngestEventRequest` / `contracts.RawEventMessage` (flat top-level fields + nested `rule` and `geoLocation`, enum names as Strings), so it is directly ingestible as a single object or an array (batch).
- **Commits:** conventional commits, imperative, lowercase, ≤72 chars. Milestone tag `v0.6-generator` at the end.

---

### Task 1: Module scaffold, config binding, bootable CLI

**Files:**
- Create: `services/event-generator/pom.xml`
- Modify: `pom.xml` (root) — append `<module>services/event-generator</module>`
- Create: `services/event-generator/src/main/java/com/akamai/wsa/generator/GeneratorApplication.java`
- Create: `services/event-generator/src/main/java/com/akamai/wsa/generator/GeneratorProperties.java`
- Create: `services/event-generator/src/main/resources/application.yml`
- Test: `services/event-generator/src/test/java/com/akamai/wsa/generator/GeneratorPropertiesTest.java`
- Remove any `.gitkeep` under the module's source dirs if present.

**Interfaces:**
- Produces: `GeneratorProperties(long seed, int totalEvents, int waveCount, int waveSize, Instant baseTimestamp, String targetUrl, int batchSize, OutputMode outputMode, String outputFile)` with `enum OutputMode { JSON_FILE, STDOUT, HTTP }`.

- [ ] **Step 1: Write the failing test**

`services/event-generator/src/test/java/com/akamai/wsa/generator/GeneratorPropertiesTest.java`
```java
package com.akamai.wsa.generator;

import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import static org.assertj.core.api.Assertions.assertThat;

class GeneratorPropertiesTest {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withUserConfiguration(TestConfig.class);

    @EnableConfigurationProperties(GeneratorProperties.class)
    static class TestConfig {
    }

    @Test
    void bindsPropertiesFromConfiguration() {
        contextRunner
                .withPropertyValues(
                        "generator.seed=42",
                        "generator.total-events=1000",
                        "generator.wave-count=5",
                        "generator.wave-size=50",
                        "generator.batch-size=200",
                        "generator.output-mode=HTTP")
                .run(context -> {
                    GeneratorProperties properties = context.getBean(GeneratorProperties.class);
                    assertThat(properties.seed()).isEqualTo(42L);
                    assertThat(properties.totalEvents()).isEqualTo(1000);
                    assertThat(properties.waveCount()).isEqualTo(5);
                    assertThat(properties.outputMode()).isEqualTo(GeneratorProperties.OutputMode.HTTP);
                });
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `mvn -q -pl services/event-generator test`
Expected: FAIL — module/types do not exist (build error).

- [ ] **Step 3: Register the module in the root `pom.xml`**

The v2 reactor already lists six modules — **append** the generator (do not replace the list):
```xml
    <modules>
        <module>services/contracts</module>
        <module>services/gateway</module>
        <module>services/enrichment</module>
        <module>services/event-store</module>
        <module>services/analytics</module>
        <module>services/mini-wsa</module>
        <module>services/event-generator</module>
    </modules>
```
(If `mini-wsa` has already been retired by the restructure Task 5, it will be absent — just ensure `services/event-generator` is present alongside the current modules.)

- [ ] **Step 4: Write the module `pom.xml`**

`services/event-generator/pom.xml`
```xml
<?xml version="1.0" encoding="UTF-8"?>
<project xmlns="http://maven.apache.org/POM/4.0.0"
         xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
         xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 http://maven.apache.org/xsd/maven-4.0.0.xsd">
    <modelVersion>4.0.0</modelVersion>

    <parent>
        <groupId>com.akamai.wsa</groupId>
        <artifactId>mini-wsa-parent</artifactId>
        <version>0.1.0-SNAPSHOT</version>
        <relativePath>../../pom.xml</relativePath>
    </parent>

    <artifactId>event-generator</artifactId>
    <name>Mini WSA Event Generator</name>

    <dependencies>
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-web</artifactId>
        </dependency>
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-test</artifactId>
            <scope>test</scope>
        </dependency>
    </dependencies>

    <build>
        <plugins>
            <plugin>
                <groupId>org.springframework.boot</groupId>
                <artifactId>spring-boot-maven-plugin</artifactId>
            </plugin>
        </plugins>
    </build>
</project>
```
(`spring-boot-starter-web` brings `RestClient` + Jackson; the app runs as a CLI and exits — no server is started, see Step 7. Optionally add a `contracts` dependency if you choose to serialize `contracts.RawEventMessage` directly — see Task 4's note.)

- [ ] **Step 5: Write `GeneratorProperties`**

`services/event-generator/src/main/java/com/akamai/wsa/generator/GeneratorProperties.java`
```java
package com.akamai.wsa.generator;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

import java.time.Instant;

@ConfigurationProperties(prefix = "generator")
public record GeneratorProperties(
        @DefaultValue("1") long seed,
        @DefaultValue("10000") int totalEvents,
        @DefaultValue("20") int waveCount,
        @DefaultValue("50") int waveSize,
        @DefaultValue("2026-05-20T14:00:00Z") Instant baseTimestamp,
        @DefaultValue("http://localhost:8081/v1/events/ingest") String targetUrl,
        @DefaultValue("500") int batchSize,
        @DefaultValue("STDOUT") OutputMode outputMode,
        @DefaultValue("generated-events.json") String outputFile
) {
    public enum OutputMode {
        JSON_FILE,
        STDOUT,
        HTTP
    }
}
```

- [ ] **Step 6: Write the application entrypoint**

`services/event-generator/src/main/java/com/akamai/wsa/generator/GeneratorApplication.java`
```java
package com.akamai.wsa.generator;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

@SpringBootApplication
@ConfigurationPropertiesScan
public class GeneratorApplication {
    public static void main(String[] args) {
        SpringApplication.run(GeneratorApplication.class, args);
    }
}
```

- [ ] **Step 7: Write `application.yml`**

`services/event-generator/src/main/resources/application.yml`
```yaml
spring:
  main:
    web-application-type: none   # CLI tool; do not start an HTTP server
generator:
  seed: 1
  total-events: 10000
  wave-count: 20
  wave-size: 50
  base-timestamp: "2026-05-20T14:00:00Z"
  target-url: "http://localhost:8081/v1/events/ingest"   # the gateway
  batch-size: 500
  output-mode: STDOUT
  output-file: "generated-events.json"
```

- [ ] **Step 8: Run the test to verify it passes**

Run: `mvn -q -pl services/event-generator test`
Expected: PASS (`GeneratorPropertiesTest.bindsPropertiesFromConfiguration`).

- [ ] **Step 9: Commit**

```bash
git add pom.xml services/event-generator/pom.xml \
        services/event-generator/src/main/java/com/akamai/wsa/generator/GeneratorApplication.java \
        services/event-generator/src/main/java/com/akamai/wsa/generator/GeneratorProperties.java \
        services/event-generator/src/main/resources/application.yml \
        services/event-generator/src/test/java/com/akamai/wsa/generator/GeneratorPropertiesTest.java
git commit -m "chore: scaffold event-generator module with bound config"
```

---

### Task 2: Event model + seed-deterministic single-event generator

**Files:**
- Create: `.../generator/model/GeneratedRule.java`, `GeneratedGeoLocation.java`, `GeneratedEvent.java`
- Create: `.../generator/generate/SecurityEventGenerator.java`
- Test: `.../generator/generate/SecurityEventGeneratorTest.java`

**Interfaces:**
- Produces:
  - `GeneratedEvent(String eventId, Instant timestamp, int configId, String policyId, String clientIp, String hostname, String path, String method, int statusCode, String userAgent, GeneratedRule rule, String action, GeneratedGeoLocation geoLocation, long requestSize, long responseSize)` — field names/order mirror `contracts.RawEventMessage` (enums as Strings on the wire).
  - `GeneratedRule(String id, String name, String message, String severity, String category)`
  - `GeneratedGeoLocation(String country, String city)`
  - `SecurityEventGenerator` with `generateNormalEvent(int sequenceNumber, java.util.Random random)` and `generateWaveEvent(int sequenceNumber, String clientIp, String path, Instant timestamp, java.util.Random random)`.

- [ ] **Step 1: Write the failing test**

`services/event-generator/src/test/java/com/akamai/wsa/generator/generate/SecurityEventGeneratorTest.java`
```java
package com.akamai.wsa.generator.generate;

import com.akamai.wsa.generator.model.GeneratedEvent;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.Random;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class SecurityEventGeneratorTest {

    private static final Set<String> VALID_CATEGORIES =
            Set.of("INJECTION", "XSS", "PROTOCOL_VIOLATION", "DATA_LEAKAGE", "BOT", "DOS", "RATE_LIMIT");
    private static final Set<String> VALID_ACTIONS = Set.of("DENY", "ALERT", "MONITOR");
    private static final Set<String> VALID_SEVERITIES = Set.of("CRITICAL", "HIGH", "MEDIUM", "LOW");

    private final SecurityEventGenerator generator =
            new SecurityEventGenerator(Instant.parse("2026-05-20T14:00:00Z"));

    @Test
    void sameSeedProducesIdenticalEvents() {
        List<GeneratedEvent> first = generateTen(new Random(99L));
        List<GeneratedEvent> second = generateTen(new Random(99L));

        assertThat(first).isEqualTo(second);
    }

    @Test
    void producesSchemaValidEvents() {
        Random random = new Random(7L);

        for (int sequenceNumber = 0; sequenceNumber < 200; sequenceNumber++) {
            GeneratedEvent event = generator.generateNormalEvent(sequenceNumber, random);

            assertThat(event.eventId()).isNotBlank();
            assertThat(event.timestamp()).isNotNull();
            assertThat(event.clientIp()).matches("\\d{1,3}\\.\\d{1,3}\\.\\d{1,3}\\.\\d{1,3}");
            assertThat(event.path()).startsWith("/");
            assertThat(VALID_CATEGORIES).contains(event.rule().category());
            assertThat(VALID_SEVERITIES).contains(event.rule().severity());
            assertThat(VALID_ACTIONS).contains(event.action());
            assertThat(event.requestSize()).isPositive();
        }
    }

    private List<GeneratedEvent> generateTen(Random random) {
        return java.util.stream.IntStream.range(0, 10)
                .mapToObj(sequenceNumber -> generator.generateNormalEvent(sequenceNumber, random))
                .toList();
    }
}
```

- [ ] **Step 2: Run test to verify it fails** — `mvn -q -pl services/event-generator test -Dtest=SecurityEventGeneratorTest` → FAIL.

- [ ] **Step 3: Write the event model records**

`.../model/GeneratedRule.java`
```java
package com.akamai.wsa.generator.model;

public record GeneratedRule(String id, String name, String message, String severity, String category) {
}
```
`.../model/GeneratedGeoLocation.java`
```java
package com.akamai.wsa.generator.model;

public record GeneratedGeoLocation(String country, String city) {
}
```
`.../model/GeneratedEvent.java`
```java
package com.akamai.wsa.generator.model;

import java.time.Instant;

public record GeneratedEvent(
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
        GeneratedRule rule,
        String action,
        GeneratedGeoLocation geoLocation,
        long requestSize,
        long responseSize
) {
}
```

- [ ] **Step 4: Write the generator**

`.../generate/SecurityEventGenerator.java`
```java
package com.akamai.wsa.generator.generate;

import com.akamai.wsa.generator.model.GeneratedEvent;
import com.akamai.wsa.generator.model.GeneratedGeoLocation;
import com.akamai.wsa.generator.model.GeneratedRule;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Random;

/**
 * Produces realistic, randomized-but-deterministic events. All randomness comes
 * from the supplied Random; timestamps derive from a fixed base instant so runs
 * are reproducible.
 */
public class SecurityEventGenerator {

    private static final List<String> CATEGORIES =
            List.of("INJECTION", "XSS", "PROTOCOL_VIOLATION", "DATA_LEAKAGE", "BOT", "DOS", "RATE_LIMIT");
    private static final List<String> SEVERITIES = List.of("CRITICAL", "HIGH", "MEDIUM", "LOW");
    private static final List<String> ACTIONS = List.of("DENY", "ALERT", "MONITOR");
    private static final List<String> METHODS = List.of("GET", "POST", "PUT", "DELETE");
    private static final List<String> PATHS = List.of(
            "/api/v1/login", "/admin", "/api/v1/users", "/checkout", "/api/v1/search",
            "/wp-admin", "/api/v1/orders", "/static/app.js", "/api/v1/payments");
    private static final List<String> HOSTNAMES = List.of("www.example.com", "api.example.com", "shop.example.com");
    private static final List<String> COUNTRIES = List.of("CN", "RU", "US", "BR", "IN", "DE");
    private static final List<Integer> CONFIG_IDS = List.of(14227, 20351, 88120);
    private static final String USER_AGENT = "Mozilla/5.0 (Windows NT 10.0; Win64; x64)";

    private final Instant baseTimestamp;

    public SecurityEventGenerator(Instant baseTimestamp) {
        this.baseTimestamp = baseTimestamp;
    }

    public GeneratedEvent generateNormalEvent(int sequenceNumber, Random random) {
        String clientIp = randomIp(random);
        String path = PATHS.get(random.nextInt(PATHS.size()));
        Instant timestamp = baseTimestamp.plus(sequenceNumber, ChronoUnit.SECONDS);
        return buildEvent(sequenceNumber, clientIp, path, timestamp, random);
    }

    public GeneratedEvent generateWaveEvent(int sequenceNumber, String clientIp, String path,
                                            Instant timestamp, Random random) {
        return buildEvent(sequenceNumber, clientIp, path, timestamp, random);
    }

    public String randomIp(Random random) {
        return (1 + random.nextInt(223)) + "." + random.nextInt(256) + "."
                + random.nextInt(256) + "." + (1 + random.nextInt(254));
    }

    private GeneratedEvent buildEvent(int sequenceNumber, String clientIp, String path,
                                      Instant timestamp, Random random) {
        String category = CATEGORIES.get(random.nextInt(CATEGORIES.size()));
        String severity = SEVERITIES.get(random.nextInt(SEVERITIES.size()));
        String action = ACTIONS.get(random.nextInt(ACTIONS.size()));
        int configId = CONFIG_IDS.get(random.nextInt(CONFIG_IDS.size()));
        return new GeneratedEvent(
                "evt-" + String.format("%08d", sequenceNumber),
                timestamp,
                configId,
                "pol_web" + (1 + random.nextInt(3)),
                clientIp,
                HOSTNAMES.get(random.nextInt(HOSTNAMES.size())),
                path,
                METHODS.get(random.nextInt(METHODS.size())),
                random.nextBoolean() ? 403 : 200,
                USER_AGENT,
                new GeneratedRule(
                        "9500" + String.format("%02d", random.nextInt(100)),
                        category + "_RULE",
                        category + " detected",
                        severity,
                        category),
                action,
                new GeneratedGeoLocation(COUNTRIES.get(random.nextInt(COUNTRIES.size())), "City"),
                64 + random.nextInt(4096),
                64 + random.nextInt(2048));
    }
}
```

- [ ] **Step 5: Run tests to verify they pass** — `mvn -q -pl services/event-generator test -Dtest=SecurityEventGeneratorTest` → PASS.

- [ ] **Step 6: Commit** — `git commit -m "feat: add seed-deterministic security event generator"`

---

### Task 3: Dataset assembly with attack waves

**Files:**
- Create: `.../generate/DatasetGenerator.java`
- Test: `.../generate/DatasetGeneratorTest.java`

**Interfaces:**
- Consumes: `SecurityEventGenerator`, `GeneratorProperties`.
- Produces: `DatasetGenerator` with `List<GeneratedEvent> generate()` — exactly `totalEvents` events, of which `waveCount` waves of `waveSize` share one clientIp + one path within a 2-minute span.

- [ ] **Step 1: Write the failing test**

`.../generate/DatasetGeneratorTest.java`
```java
package com.akamai.wsa.generator.generate;

import com.akamai.wsa.generator.GeneratorProperties;
import com.akamai.wsa.generator.model.GeneratedEvent;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

class DatasetGeneratorTest {

    private GeneratorProperties propertiesWith(int totalEvents, int waveCount, int waveSize) {
        return new GeneratorProperties(
                123L, totalEvents, waveCount, waveSize,
                Instant.parse("2026-05-20T14:00:00Z"),
                "http://localhost:8081/v1/events/ingest", 500,
                GeneratorProperties.OutputMode.STDOUT, "out.json");
    }

    @Test
    void producesExactlyTheRequestedTotal() {
        DatasetGenerator datasetGenerator = new DatasetGenerator(propertiesWith(1000, 5, 40));

        assertThat(datasetGenerator.generate()).hasSize(1000);
    }

    @Test
    void sameSeedProducesIdenticalDataset() {
        assertThat(new DatasetGenerator(propertiesWith(500, 3, 30).withSeed(55L)).generate())
                .isEqualTo(new DatasetGenerator(propertiesWith(500, 3, 30).withSeed(55L)).generate());
    }

    @Test
    void containsAtLeastOneRepeatOffenderBurst() {
        DatasetGenerator datasetGenerator = new DatasetGenerator(propertiesWith(600, 4, 30));

        List<GeneratedEvent> events = datasetGenerator.generate();

        boolean hasBurst = events.stream()
                .collect(Collectors.groupingBy(event -> event.clientIp() + "|" + event.path()))
                .values().stream()
                .anyMatch(group -> group.size() > 5 && withinTenMinutes(group));

        assertThat(hasBurst).isTrue();
    }

    private boolean withinTenMinutes(List<GeneratedEvent> group) {
        Instant earliest = group.stream().map(GeneratedEvent::timestamp).min(Instant::compareTo).orElseThrow();
        Instant latest = group.stream().map(GeneratedEvent::timestamp).max(Instant::compareTo).orElseThrow();
        return Duration.between(earliest, latest).compareTo(Duration.ofMinutes(10)) <= 0;
    }
}
```
(Add a `withSeed` copy helper to `GeneratorProperties` in Step 3.)

- [ ] **Step 2: Run test to verify it fails** — `mvn -q -pl services/event-generator test -Dtest=DatasetGeneratorTest` → FAIL.

- [ ] **Step 3: Add `withSeed` to `GeneratorProperties`**
```java
    public GeneratorProperties withSeed(long newSeed) {
        return new GeneratorProperties(newSeed, totalEvents, waveCount, waveSize, baseTimestamp,
                targetUrl, batchSize, outputMode, outputFile);
    }
```

- [ ] **Step 4: Write `DatasetGenerator`**

`.../generate/DatasetGenerator.java`
```java
package com.akamai.wsa.generator.generate;

import com.akamai.wsa.generator.GeneratorProperties;
import com.akamai.wsa.generator.model.GeneratedEvent;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * Assembles a full dataset: background events plus {@code waveCount} attack
 * waves, each a burst of {@code waveSize} events from one client IP hitting one
 * path within a two-minute span. Deterministic for a given seed.
 */
@Component
public class DatasetGenerator {

    private static final List<String> WAVE_PATHS = List.of("/api/v1/login", "/admin", "/checkout");
    private static final int WAVE_SPAN_SECONDS = 120;

    private final GeneratorProperties properties;
    private final SecurityEventGenerator eventGenerator;

    public DatasetGenerator(GeneratorProperties properties) {
        this.properties = properties;
        this.eventGenerator = new SecurityEventGenerator(properties.baseTimestamp());
    }

    public List<GeneratedEvent> generate() {
        Random random = new Random(properties.seed());
        int waveEventTotal = Math.min(properties.waveCount() * properties.waveSize(), properties.totalEvents());
        int backgroundTotal = properties.totalEvents() - waveEventTotal;

        List<GeneratedEvent> events = new ArrayList<>(properties.totalEvents());
        int sequenceNumber = 0;

        for (int backgroundIndex = 0; backgroundIndex < backgroundTotal; backgroundIndex++) {
            events.add(eventGenerator.generateNormalEvent(sequenceNumber++, random));
        }

        int remainingWaveEvents = waveEventTotal;
        for (int waveIndex = 0; waveIndex < properties.waveCount() && remainingWaveEvents > 0; waveIndex++) {
            String attackerIp = eventGenerator.randomIp(random);
            String targetPath = WAVE_PATHS.get(random.nextInt(WAVE_PATHS.size()));
            Instant waveStart = properties.baseTimestamp().plus(waveIndex, ChronoUnit.HOURS);
            int eventsInThisWave = Math.min(properties.waveSize(), remainingWaveEvents);
            for (int burstIndex = 0; burstIndex < eventsInThisWave; burstIndex++) {
                Instant timestamp = waveStart.plusSeconds(random.nextInt(WAVE_SPAN_SECONDS));
                events.add(eventGenerator.generateWaveEvent(sequenceNumber++, attackerIp, targetPath, timestamp, random));
            }
            remainingWaveEvents -= eventsInThisWave;
        }

        return List.copyOf(events);
    }
}
```

- [ ] **Step 5: Run tests to verify they pass** — PASS (exact total, determinism, repeat-offender burst present).

- [ ] **Step 6: Commit** — `git commit -m "feat: assemble dataset with attack waves"`

---

### Task 4: JSON output writer

**Files:**
- Create: `.../output/JsonEventWriter.java`
- Test: `.../output/JsonEventWriterTest.java`

**Interfaces:**
- Produces: `JsonEventWriter` with `String toJsonArray(List<GeneratedEvent> events)` — serializes to the gateway's ingest request array shape (ISO-8601 timestamps, nested rule/geoLocation).

> **Contract-alignment note:** `GeneratedEvent`'s field names/order intentionally
> mirror `contracts.RawEventMessage` and the gateway's `IngestEventRequest`, so the
> serialized JSON is accepted as-is. If you prefer zero drift risk, add the
> `contracts` dependency and map `GeneratedEvent` → `contracts.RawEventMessage`
> before serializing. Either way, any `contracts` change (see its CHANGELOG) means
> re-checking this shape.

- [ ] **Step 1: Write the failing test**

`.../output/JsonEventWriterTest.java`
```java
package com.akamai.wsa.generator.output;

import com.akamai.wsa.generator.generate.SecurityEventGenerator;
import com.akamai.wsa.generator.model.GeneratedEvent;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.Random;

import static org.assertj.core.api.Assertions.assertThat;

class JsonEventWriterTest {

    private final ObjectMapper objectMapper = JsonMapper.builder()
            .addModule(new JavaTimeModule())
            .build();
    private final JsonEventWriter writer = new JsonEventWriter(objectMapper);

    @Test
    void serializesEventsToIngestibleJsonArray() throws Exception {
        GeneratedEvent event = new SecurityEventGenerator(Instant.parse("2026-05-20T14:00:00Z"))
                .generateNormalEvent(0, new Random(1L));

        String json = writer.toJsonArray(List.of(event));
        JsonNode array = objectMapper.readTree(json);
        JsonNode first = array.get(0);

        assertThat(array.isArray()).isTrue();
        assertThat(first.get("eventId").asText()).isEqualTo(event.eventId());
        assertThat(first.get("rule").get("category").asText()).isEqualTo(event.rule().category());
        assertThat(first.get("timestamp").asText()).isEqualTo("2026-05-20T14:00:00Z");
    }
}
```

- [ ] **Step 2: Run test to verify it fails** → FAIL.

- [ ] **Step 3: Write `JsonEventWriter`**

`.../output/JsonEventWriter.java`
```java
package com.akamai.wsa.generator.output;

import com.akamai.wsa.generator.model.GeneratedEvent;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class JsonEventWriter {

    private final ObjectMapper objectMapper;

    public JsonEventWriter(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public String toJsonArray(List<GeneratedEvent> events) {
        try {
            return objectMapper.writeValueAsString(events);
        } catch (JsonProcessingException serializationFailure) {
            throw new IllegalStateException("failed to serialize generated events", serializationFailure);
        }
    }
}
```
(Spring Boot auto-registers a `JavaTimeModule`-configured `ObjectMapper`, so the injected mapper emits ISO-8601 timestamps.)

- [ ] **Step 4: Run test to verify it passes** → PASS.

- [ ] **Step 5: Commit** — `git commit -m "feat: serialize generated events to ingestible json"`

---

### Task 5: Batched HTTP feeder (201/400-aware) + CLI runner (+ tag)

**Files:**
- Create: `.../feed/IngestionClient.java`, `RestClientIngestionClient.java`, `IngestionFeeder.java`
- Create: `.../GeneratorRunner.java`
- Test: `.../feed/IngestionFeederTest.java`, `.../feed/RestClientIngestionClientTest.java`

**Interfaces:**
- `interface IngestionClient { int postBatch(List<GeneratedEvent> batch); }` (returns accepted count; a rejected batch returns 0, never throws)
- `RestClientIngestionClient implements IngestionClient` — POSTs to `targetUrl`, parses `201 {acceptedCount}`, and **catches `400`** (all-or-nothing batch rejection) by logging and returning 0.
- `IngestionFeeder` with `int feed(List<GeneratedEvent> events, int batchSize)` — splits into batches, sums accepted counts.
- `GeneratorRunner implements CommandLineRunner` — dispatches on `OutputMode`.

- [ ] **Step 1: Write the failing feeder test**

`.../feed/IngestionFeederTest.java`
```java
package com.akamai.wsa.generator.feed;

import com.akamai.wsa.generator.model.GeneratedEvent;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class IngestionFeederTest {

    @Test
    void postsInBatchesAndSumsAcceptedCounts() {
        RecordingIngestionClient recordingClient = new RecordingIngestionClient();
        IngestionFeeder feeder = new IngestionFeeder(recordingClient);

        int accepted = feeder.feed(eventList(250), 100);

        assertThat(recordingClient.batchSizes).containsExactly(100, 100, 50);
        assertThat(accepted).isEqualTo(250);
    }

    @Test
    void continuesWhenABatchIsRejected() {
        // a client that rejects (returns 0) must not abort the run; remaining batches still post
        IngestionClient rejectingThenAccepting = new IngestionClient() {
            private int call = 0;
            @Override
            public int postBatch(List<GeneratedEvent> batch) {
                return (call++ == 0) ? 0 : batch.size();
            }
        };
        IngestionFeeder feeder = new IngestionFeeder(rejectingThenAccepting);

        int accepted = feeder.feed(eventList(250), 100);

        assertThat(accepted).isEqualTo(150); // first batch rejected (0), next two accepted (100+50)
    }

    private List<GeneratedEvent> eventList(int count) {
        List<GeneratedEvent> events = new ArrayList<>();
        for (int sequenceNumber = 0; sequenceNumber < count; sequenceNumber++) {
            events.add(new GeneratedEvent("evt-" + sequenceNumber, Instant.parse("2026-05-20T14:00:00Z"),
                    14227, "pol", "1.1.1.1", "h", "/p", "GET", 200, "ua", null, "DENY", null, 1, 1));
        }
        return events;
    }

    private static final class RecordingIngestionClient implements IngestionClient {
        private final List<Integer> batchSizes = new ArrayList<>();

        @Override
        public int postBatch(List<GeneratedEvent> batch) {
            batchSizes.add(batch.size());
            return batch.size();
        }
    }
}
```

- [ ] **Step 2: Run test to verify it fails** → FAIL (`IngestionClient`/`IngestionFeeder` missing).

- [ ] **Step 3: Write the client port**

`.../feed/IngestionClient.java`
```java
package com.akamai.wsa.generator.feed;

import com.akamai.wsa.generator.model.GeneratedEvent;

import java.util.List;

public interface IngestionClient {

    /** POSTs a batch; returns the accepted count (0 if the batch was rejected). Never throws on a 4xx. */
    int postBatch(List<GeneratedEvent> batch);
}
```

- [ ] **Step 4: Write the feeder**

`.../feed/IngestionFeeder.java`
```java
package com.akamai.wsa.generator.feed;

import com.akamai.wsa.generator.model.GeneratedEvent;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class IngestionFeeder {

    private final IngestionClient ingestionClient;

    public IngestionFeeder(IngestionClient ingestionClient) {
        this.ingestionClient = ingestionClient;
    }

    public int feed(List<GeneratedEvent> events, int batchSize) {
        int totalAccepted = 0;
        for (int start = 0; start < events.size(); start += batchSize) {
            int end = Math.min(start + batchSize, events.size());
            totalAccepted += ingestionClient.postBatch(events.subList(start, end));
        }
        return totalAccepted;
    }
}
```

- [ ] **Step 5: Run test to verify it passes** → PASS (batching + reject-and-continue).

- [ ] **Step 6: Write the real HTTP client (201-parse + 400-catch), TDD**

Failing test `.../feed/RestClientIngestionClientTest.java`:
```java
package com.akamai.wsa.generator.feed;

import com.akamai.wsa.generator.GeneratorProperties;
import com.akamai.wsa.generator.model.GeneratedEvent;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

class RestClientIngestionClientTest {

    private static final String URL = "http://localhost:8081/v1/events/ingest";

    private GeneratorProperties props() {
        return new GeneratorProperties(1L, 1, 0, 0, Instant.parse("2026-05-20T14:00:00Z"),
                URL, 500, GeneratorProperties.OutputMode.HTTP, "out.json");
    }

    private GeneratedEvent anEvent() {
        return new GeneratedEvent("evt-1", Instant.parse("2026-05-20T14:00:00Z"), 14227, "pol",
                "1.1.1.1", "h", "/p", "GET", 200, "ua", null, "DENY", null, 1, 1);
    }

    @Test
    void parsesAcceptedCountFrom201() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        server.expect(requestTo(URL))
                .andRespond(withStatus(HttpStatus.CREATED)
                        .contentType(MediaType.APPLICATION_JSON)
                        .body("{\"acceptedCount\":3}"));

        int accepted = new RestClientIngestionClient(builder, props()).postBatch(List.of(anEvent()));

        assertThat(accepted).isEqualTo(3);
        server.verify();
    }

    @Test
    void returnsZeroAndDoesNotThrowOn400() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        server.expect(requestTo(URL))
                .andRespond(withStatus(HttpStatus.BAD_REQUEST)
                        .contentType(MediaType.APPLICATION_JSON)
                        .body("{\"error\":{\"code\":\"VALIDATION\",\"message\":\"bad batch\"}}"));

        int accepted = new RestClientIngestionClient(builder, props()).postBatch(List.of(anEvent()));

        assertThat(accepted).isZero();
        server.verify();
    }
}
```
Implementation `.../feed/RestClientIngestionClient.java`:
```java
package com.akamai.wsa.generator.feed;

import com.akamai.wsa.generator.GeneratorProperties;
import com.akamai.wsa.generator.model.GeneratedEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

import java.util.List;

@Component
public class RestClientIngestionClient implements IngestionClient {

    private static final Logger logger = LoggerFactory.getLogger(RestClientIngestionClient.class);

    private final RestClient restClient;
    private final String targetUrl;

    public RestClientIngestionClient(RestClient.Builder restClientBuilder, GeneratorProperties properties) {
        this.restClient = restClientBuilder.build();
        this.targetUrl = properties.targetUrl();
    }

    public record IngestAcceptedResponse(int acceptedCount) {
    }

    @Override
    public int postBatch(List<GeneratedEvent> batch) {
        try {
            IngestAcceptedResponse response = restClient.post()
                    .uri(targetUrl)
                    .body(batch)
                    .retrieve()
                    .body(IngestAcceptedResponse.class);
            return response == null ? 0 : response.acceptedCount();
        } catch (RestClientResponseException rejection) {
            // Gateway is all-or-nothing per batch: one invalid event -> 400 for the whole batch.
            // Log and continue rather than aborting the run.
            logger.warn("RestClientIngestionClient - batch rejected (size={}, status={})",
                    batch.size(), rejection.getStatusCode());
            return 0;
        }
    }
}
```

- [ ] **Step 7: Write the CLI runner**

`.../GeneratorRunner.java`
```java
package com.akamai.wsa.generator;

import com.akamai.wsa.generator.feed.IngestionFeeder;
import com.akamai.wsa.generator.generate.DatasetGenerator;
import com.akamai.wsa.generator.model.GeneratedEvent;
import com.akamai.wsa.generator.output.JsonEventWriter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

@Component
public class GeneratorRunner implements CommandLineRunner {

    private static final Logger logger = LoggerFactory.getLogger(GeneratorRunner.class);

    private final GeneratorProperties properties;
    private final DatasetGenerator datasetGenerator;
    private final JsonEventWriter jsonEventWriter;
    private final IngestionFeeder ingestionFeeder;

    public GeneratorRunner(GeneratorProperties properties, DatasetGenerator datasetGenerator,
                           JsonEventWriter jsonEventWriter, IngestionFeeder ingestionFeeder) {
        this.properties = properties;
        this.datasetGenerator = datasetGenerator;
        this.jsonEventWriter = jsonEventWriter;
        this.ingestionFeeder = ingestionFeeder;
    }

    @Override
    public void run(String... arguments) throws Exception {
        List<GeneratedEvent> events = datasetGenerator.generate();
        logger.info("GeneratorRunner - generated {} events (mode={})", events.size(), properties.outputMode());
        switch (properties.outputMode()) {
            case STDOUT -> System.out.println(jsonEventWriter.toJsonArray(events));
            case JSON_FILE -> Files.writeString(Path.of(properties.outputFile()), jsonEventWriter.toJsonArray(events));
            case HTTP -> {
                int accepted = ingestionFeeder.feed(events, properties.batchSize());
                logger.info("GeneratorRunner - fed events to gateway, accepted={} of {}", accepted, events.size());
            }
        }
    }
}
```

- [ ] **Step 8: Full build, then a manual dry run against the gateway**

Run: `mvn -q clean verify` → BUILD SUCCESS, all generator tests green.

Manual end-to-end (optional): start the **gateway** on 8081 (for a full-pipeline run also bring up enrichment + event-store + Kafka via `docker compose up`), then
`mvn -q -pl services/event-generator spring-boot:run -Dspring-boot.run.arguments="--generator.output-mode=HTTP --generator.total-events=1000"`
Expected log: `accepted=1000 of 1000` (gateway accepted for processing; persistence is verified downstream via event-store/analytics).

- [ ] **Step 9: Commit and tag**

```bash
git add services/event-generator/src/main/java/com/akamai/wsa/generator/feed \
        services/event-generator/src/main/java/com/akamai/wsa/generator/GeneratorRunner.java \
        services/event-generator/src/test/java/com/akamai/wsa/generator/feed
git commit -m "feat: add 201/400-aware http feeder and cli runner"
git tag v0.6-generator
```

---

## Self-Review

**Spec coverage:** realistic randomized events ✓ (Task 2); attack waves (same IP+path burst, >5-in-10-min) ✓ (Task 3); configurable count/waves/seed/batch ✓ (Task 1); JSON file/stdout output ✓ (Task 4); batched POST to the gateway with accurate `acceptedCount` and reject-and-continue ✓ (Task 5); feedable shape mirrors `contracts.RawEventMessage` / gateway `IngestEventRequest` ✓ (Task 4).

**v2 reconciliation fixes applied:** root-pom snippet now appends to the six-module reactor (Task 1 Step 3); all "mini-wsa on 8081" → "gateway on 8081" with a full-pipeline note; feeder parses `201 {acceptedCount}` and **catches `400`** to avoid aborting the run (Task 5); milestone tag `v0.6-generator`.

**Placeholder scan:** none. **Type consistency:** `GeneratedEvent` component order/names identical across model, generator, writer, feeder, tests; `IngestionClient.postBatch(List<GeneratedEvent>)` matches feeder + fakes + REST impl; `RestClientIngestionClient` constructor takes `(RestClient.Builder, GeneratorProperties)` for testability.

**Open notes:** direct-Kafka producer output mode is a deferred future add (SDD §2). Optionally inject a small fraction of invalid events (config flag) to exercise the gateway's 400 path as negative-test data.
