# Part 5 — Data Generator Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** A standalone `event-generator` service that produces realistic, seed-deterministic security events — including attack waves from a single IP hitting a single path — and either writes them as JSON or POSTs them in batches to the ingestion API.

**Architecture:** A second Spring Boot module in the mono-repo (`services/event-generator`, package `com.akamai.wsa.generator`). Generation is pure and seeded (`java.util.Random` + a fixed base timestamp), so it is fully testable without a clock or a live server. Output is split behind small units: a `JsonEventWriter` (file/stdout) and an `IngestionFeeder` that drives an `IngestionClient` port (real `RestClient` impl; faked in tests). A `CommandLineRunner` wires config → generate → output.

**Tech Stack:** Java 21, Spring Boot 3.3.5, Jackson (records + JavaTimeModule), JUnit 5 + AssertJ.

## Global Constraints

- **Java version:** 21. `maven.compiler.release=21` (inherited from `mini-wsa-parent`).
- **Base package:** `com.akamai.wsa.generator`.
- **Module version:** `0.1.0-SNAPSHOT`; parent = `mini-wsa-parent` (`../../pom.xml`).
- **Determinism:** all randomness flows from an injected `java.util.Random` seeded from config; timestamps derive from a configured base `Instant` + offsets. NEVER use `Instant.now()`, `Math.random()`, or unseeded `Random` in generation code. Same seed → identical events.
- **Immutability:** events and config are `record`s; no mutation of inputs.
- **Naming:** intention-revealing; FULL descriptive parameter names (no `v`, `e`, `idx` — use `index`, `random`, `securityEvent`).
- **Ingest target:** mini-wsa runs locally on **8081**; default target URL `http://localhost:8081/v1/events/ingest`.
- **Event JSON shape:** must match the assignment's DLR schema exactly (flat top-level fields + nested `rule` and `geoLocation`), so it is directly ingestible.
- **Commits:** conventional commits, imperative, lowercase, ≤72 chars. Milestone tag `v0.5-generator` at the end.

---

### Task 1: Module scaffold, config binding, bootable CLI

**Files:**
- Create: `services/event-generator/pom.xml`
- Modify: `pom.xml` (root) — add `<module>services/event-generator</module>`
- Create: `services/event-generator/src/main/java/com/akamai/wsa/generator/GeneratorApplication.java`
- Create: `services/event-generator/src/main/java/com/akamai/wsa/generator/GeneratorProperties.java`
- Create: `services/event-generator/src/main/resources/application.yml`
- Test: `services/event-generator/src/test/java/com/akamai/wsa/generator/GeneratorPropertiesTest.java`
- Remove: `services/event-generator/src/main/java/com/akamai/wsa/generator/.gitkeep` (and the test `.gitkeep`) if present

**Interfaces:**
- Consumes: nothing.
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

In `pom.xml` `<modules>`, add the generator alongside mini-wsa:
```xml
    <modules>
        <module>services/mini-wsa</module>
        <module>services/event-generator</module>
    </modules>
```

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
            <artifactId>spring-boot-starter</artifactId>
        </dependency>
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
(`spring-boot-starter-web` brings `RestClient` + Jackson; the app runs as a CLI and exits, so no server is started — see Step 6.)

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
  target-url: "http://localhost:8081/v1/events/ingest"
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
- Create: `services/event-generator/src/main/java/com/akamai/wsa/generator/model/GeneratedRule.java`
- Create: `services/event-generator/src/main/java/com/akamai/wsa/generator/model/GeneratedGeoLocation.java`
- Create: `services/event-generator/src/main/java/com/akamai/wsa/generator/model/GeneratedEvent.java`
- Create: `services/event-generator/src/main/java/com/akamai/wsa/generator/generate/SecurityEventGenerator.java`
- Test: `services/event-generator/src/test/java/com/akamai/wsa/generator/generate/SecurityEventGeneratorTest.java`

**Interfaces:**
- Consumes: nothing.
- Produces:
  - `GeneratedEvent(String eventId, Instant timestamp, int configId, String policyId, String clientIp, String hostname, String path, String method, int statusCode, String userAgent, GeneratedRule rule, String action, GeneratedGeoLocation geoLocation, long requestSize, long responseSize)`
  - `GeneratedRule(String id, String name, String message, String severity, String category)`
  - `GeneratedGeoLocation(String country, String city)`
  - `SecurityEventGenerator` with `GeneratedEvent generateNormalEvent(int sequenceNumber, java.util.Random random)` and `GeneratedEvent generateWaveEvent(int sequenceNumber, String clientIp, String path, Instant timestamp, java.util.Random random)`.

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

- [ ] **Step 2: Run test to verify it fails**

Run: `mvn -q -pl services/event-generator test -Dtest=SecurityEventGeneratorTest`
Expected: FAIL — types do not exist.

- [ ] **Step 3: Write the event model records**

`services/event-generator/src/main/java/com/akamai/wsa/generator/model/GeneratedRule.java`
```java
package com.akamai.wsa.generator.model;

public record GeneratedRule(String id, String name, String message, String severity, String category) {
}
```

`services/event-generator/src/main/java/com/akamai/wsa/generator/model/GeneratedGeoLocation.java`
```java
package com.akamai.wsa.generator.model;

public record GeneratedGeoLocation(String country, String city) {
}
```

`services/event-generator/src/main/java/com/akamai/wsa/generator/model/GeneratedEvent.java`
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

`services/event-generator/src/main/java/com/akamai/wsa/generator/generate/SecurityEventGenerator.java`
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
    private static final List<int[]> CONFIG_IDS = List.of(new int[]{14227}, new int[]{20351}, new int[]{88120});
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
        int configId = CONFIG_IDS.get(random.nextInt(CONFIG_IDS.size()))[0];
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

- [ ] **Step 5: Run tests to verify they pass**

Run: `mvn -q -pl services/event-generator test -Dtest=SecurityEventGeneratorTest`
Expected: PASS (determinism + schema validity).

- [ ] **Step 6: Commit**

```bash
git add services/event-generator/src/main/java/com/akamai/wsa/generator/model \
        services/event-generator/src/main/java/com/akamai/wsa/generator/generate/SecurityEventGenerator.java \
        services/event-generator/src/test/java/com/akamai/wsa/generator/generate/SecurityEventGeneratorTest.java
git commit -m "feat: add seed-deterministic security event generator"
```

---

### Task 3: Dataset assembly with attack waves

**Files:**
- Create: `services/event-generator/src/main/java/com/akamai/wsa/generator/generate/DatasetGenerator.java`
- Test: `services/event-generator/src/test/java/com/akamai/wsa/generator/generate/DatasetGeneratorTest.java`

**Interfaces:**
- Consumes: `SecurityEventGenerator`, `GeneratorProperties`.
- Produces: `DatasetGenerator` with `List<GeneratedEvent> generate()` — produces exactly `totalEvents` events, of which `waveCount` waves of `waveSize` events each share one clientIp + one path within a 2-minute span.

- [ ] **Step 1: Write the failing test**

`services/event-generator/src/test/java/com/akamai/wsa/generator/generate/DatasetGeneratorTest.java`
```java
package com.akamai.wsa.generator.generate;

import com.akamai.wsa.generator.GeneratorProperties;
import com.akamai.wsa.generator.model.GeneratedEvent;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
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

- [ ] **Step 2: Run test to verify it fails**

Run: `mvn -q -pl services/event-generator test -Dtest=DatasetGeneratorTest`
Expected: FAIL — `DatasetGenerator` / `withSeed` do not exist.

- [ ] **Step 3: Add `withSeed` to `GeneratorProperties`**

Add this method inside the `GeneratorProperties` record body:
```java
    public GeneratorProperties withSeed(long newSeed) {
        return new GeneratorProperties(newSeed, totalEvents, waveCount, waveSize, baseTimestamp,
                targetUrl, batchSize, outputMode, outputFile);
    }
```

- [ ] **Step 4: Write `DatasetGenerator`**

`services/event-generator/src/main/java/com/akamai/wsa/generator/generate/DatasetGenerator.java`
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

- [ ] **Step 5: Run tests to verify they pass**

Run: `mvn -q -pl services/event-generator test -Dtest=DatasetGeneratorTest`
Expected: PASS (exact total, determinism, repeat-offender burst present).

- [ ] **Step 6: Commit**

```bash
git add services/event-generator/src/main/java/com/akamai/wsa/generator/GeneratorProperties.java \
        services/event-generator/src/main/java/com/akamai/wsa/generator/generate/DatasetGenerator.java \
        services/event-generator/src/test/java/com/akamai/wsa/generator/generate/DatasetGeneratorTest.java
git commit -m "feat: assemble dataset with attack waves"
```

---

### Task 4: JSON output writer

**Files:**
- Create: `services/event-generator/src/main/java/com/akamai/wsa/generator/output/JsonEventWriter.java`
- Test: `services/event-generator/src/test/java/com/akamai/wsa/generator/output/JsonEventWriterTest.java`

**Interfaces:**
- Consumes: `com.fasterxml.jackson.databind.ObjectMapper`.
- Produces: `JsonEventWriter` with `String toJsonArray(List<GeneratedEvent> events)` — serializes to the ingest request array shape (ISO-8601 timestamps, nested rule/geoLocation).

- [ ] **Step 1: Write the failing test**

`services/event-generator/src/test/java/com/akamai/wsa/generator/output/JsonEventWriterTest.java`
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

- [ ] **Step 2: Run test to verify it fails**

Run: `mvn -q -pl services/event-generator test -Dtest=JsonEventWriterTest`
Expected: FAIL — `JsonEventWriter` does not exist.

- [ ] **Step 3: Write `JsonEventWriter`**

`services/event-generator/src/main/java/com/akamai/wsa/generator/output/JsonEventWriter.java`
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
(Spring Boot auto-registers a `JavaTimeModule`-configured `ObjectMapper` bean, so the injected mapper emits ISO-8601 timestamps.)

- [ ] **Step 4: Run test to verify it passes**

Run: `mvn -q -pl services/event-generator test -Dtest=JsonEventWriterTest`
Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add services/event-generator/src/main/java/com/akamai/wsa/generator/output/JsonEventWriter.java \
        services/event-generator/src/test/java/com/akamai/wsa/generator/output/JsonEventWriterTest.java
git commit -m "feat: serialize generated events to ingestible json"
```

---

### Task 5: Batched HTTP feeder + CLI runner (+ tag)

**Files:**
- Create: `services/event-generator/src/main/java/com/akamai/wsa/generator/feed/IngestionClient.java`
- Create: `services/event-generator/src/main/java/com/akamai/wsa/generator/feed/RestClientIngestionClient.java`
- Create: `services/event-generator/src/main/java/com/akamai/wsa/generator/feed/IngestionFeeder.java`
- Create: `services/event-generator/src/main/java/com/akamai/wsa/generator/GeneratorRunner.java`
- Test: `services/event-generator/src/test/java/com/akamai/wsa/generator/feed/IngestionFeederTest.java`

**Interfaces:**
- Consumes: `DatasetGenerator`, `JsonEventWriter`, `GeneratorProperties`.
- Produces:
  - `interface IngestionClient { int postBatch(List<GeneratedEvent> batch); }` (returns accepted count)
  - `RestClientIngestionClient implements IngestionClient` (posts to `targetUrl`)
  - `IngestionFeeder` with `int feed(List<GeneratedEvent> events, int batchSize)` — splits into batches, sums accepted counts
  - `GeneratorRunner implements CommandLineRunner` — dispatches on `OutputMode`

- [ ] **Step 1: Write the failing test**

`services/event-generator/src/test/java/com/akamai/wsa/generator/feed/IngestionFeederTest.java`
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

- [ ] **Step 2: Run test to verify it fails**

Run: `mvn -q -pl services/event-generator test -Dtest=IngestionFeederTest`
Expected: FAIL — `IngestionClient` / `IngestionFeeder` do not exist.

- [ ] **Step 3: Write the client port**

`services/event-generator/src/main/java/com/akamai/wsa/generator/feed/IngestionClient.java`
```java
package com.akamai.wsa.generator.feed;

import com.akamai.wsa.generator.model.GeneratedEvent;

import java.util.List;

public interface IngestionClient {

    int postBatch(List<GeneratedEvent> batch);
}
```

- [ ] **Step 4: Write the feeder**

`services/event-generator/src/main/java/com/akamai/wsa/generator/feed/IngestionFeeder.java`
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

- [ ] **Step 5: Run test to verify it passes**

Run: `mvn -q -pl services/event-generator test -Dtest=IngestionFeederTest`
Expected: PASS.

- [ ] **Step 6: Write the real HTTP client**

`services/event-generator/src/main/java/com/akamai/wsa/generator/feed/RestClientIngestionClient.java`
```java
package com.akamai.wsa.generator.feed;

import com.akamai.wsa.generator.GeneratorProperties;
import com.akamai.wsa.generator.model.GeneratedEvent;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.util.List;

@Component
public class RestClientIngestionClient implements IngestionClient {

    private final RestClient restClient;
    private final String targetUrl;

    public RestClientIngestionClient(GeneratorProperties properties) {
        this.restClient = RestClient.create();
        this.targetUrl = properties.targetUrl();
    }

    @Override
    public int postBatch(List<GeneratedEvent> batch) {
        restClient.post()
                .uri(targetUrl)
                .body(batch)
                .retrieve()
                .toBodilessEntity();
        return batch.size();
    }
}
```

- [ ] **Step 7: Write the CLI runner**

`services/event-generator/src/main/java/com/akamai/wsa/generator/GeneratorRunner.java`
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
                logger.info("GeneratorRunner - fed events to ingest API, accepted={}", accepted);
            }
        }
    }
}
```

- [ ] **Step 8: Full build, then run against a live mini-wsa (manual dry run)**

Run: `mvn -q clean verify`
Expected: BUILD SUCCESS, all generator tests green.

Manual end-to-end (optional): start mini-wsa on 8081, then
`mvn -q -pl services/event-generator spring-boot:run -Dspring-boot.run.arguments="--generator.output-mode=HTTP --generator.total-events=1000"`
Expected log: `accepted=1000`.

- [ ] **Step 9: Commit and tag**

```bash
git add services/event-generator/src/main/java/com/akamai/wsa/generator/feed \
        services/event-generator/src/main/java/com/akamai/wsa/generator/GeneratorRunner.java \
        services/event-generator/src/test/java/com/akamai/wsa/generator/feed/IngestionFeederTest.java
git commit -m "feat: add batched http feeder and cli runner"
git tag v0.5-generator
```

---

## Self-Review

**Spec coverage:** realistic randomized events ✓ (Task 2); attack waves (same IP+path burst) ✓ (Task 3); configurable count/waves/seed/batch ✓ (Task 1 props); output as JSON file/stdout ✓ (Task 4) and POST to ingest API ✓ (Task 5); feedable shape matches DLR schema ✓ (Task 4 asserts field names + ISO timestamp).

**Placeholder scan:** none — every step has concrete code and commands.

**Type consistency:** `GeneratedEvent` component order/names identical across model, generator, writer, feeder, and tests; `GeneratorProperties` accessors (`seed()`, `totalEvents()`, `waveCount()`, `waveSize()`, `baseTimestamp()`, `targetUrl()`, `batchSize()`, `outputMode()`, `outputFile()`) used consistently; `IngestionClient.postBatch(List<GeneratedEvent>)` matches feeder + fake + REST impl.

**Notes for the parent:**
- The generated JSON must stay in lockstep with Part 1's ingest request DTO. If Part 1 names a field differently (e.g. wraps single vs array), reconcile there — this plan targets the assignment's documented DLR shape and posts an array (batch), which Part 1 must accept.
- `RestClientIngestionClient` treats a 2xx as "all accepted" (returns batch size). If Part 1's response returns an explicit accepted count / per-item report, refine `postBatch` to parse it.
