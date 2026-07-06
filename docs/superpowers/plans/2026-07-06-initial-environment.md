# Initial Environment (Walking Skeleton) Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Stand up a runnable, testable Mini WSA environment — a Maven multi-module Spring Boot app with an in-memory storage adapter behind a domain port — so all later business logic has a home and can be dry-run immediately.

**Architecture:** Clean/Onion layering (`domain → application → infrastructure → interfaces`). The `domain` package has zero framework/storage imports. Persistence sits behind a domain-owned `EventRepository` **port**; the walking skeleton ships an **in-memory adapter** so the DB is swappable later (Postgres/Mongo/…) without touching domain or application code.

**Tech Stack:** Java 21, Spring Boot 3.3.5 (web, validation, actuator, test), Maven multi-module, JUnit 5 + MockMvc, Docker + Docker Compose.

## Global Constraints

- **Java version:** 21 (LTS). `maven.compiler.release=21`.
- **Base package:** `com.akamai.wsa` (mini-wsa service).
- **`domain` package imports nothing from Spring, Jackson, or any storage lib.**
- **Immutability:** value/domain types are `record`s; no setters; never mutate inputs.
- **Commits:** conventional commits, imperative, lowercase, ≤72 chars. No `[UN-XXXX]` prefix, no Co-Authored-By.
- **API responses:** literal shapes as documented (no `{success,data}` envelope).
- **Storage:** in-memory only in this plan; real DBs are a later phase behind the same port.
- **Version:** all modules `0.1.0-SNAPSHOT`.

---

### Task 1: Maven multi-module build that boots and answers `/v1/ping`

**Files:**
- Create: `pom.xml` (root aggregator/parent)
- Create: `services/mini-wsa/pom.xml`
- Create: `services/mini-wsa/src/main/java/com/akamai/wsa/MiniWsaApplication.java`
- Create: `services/mini-wsa/src/main/java/com/akamai/wsa/interfaces/rest/PingController.java`
- Create: `services/mini-wsa/src/main/resources/application.yml`
- Test: `services/mini-wsa/src/test/java/com/akamai/wsa/interfaces/rest/PingControllerTest.java`
- Remove: `services/mini-wsa/src/main/java/com/akamai/wsa/interfaces/rest/.gitkeep` (and any `.gitkeep` in dirs that now hold real files)

**Interfaces:**
- Consumes: nothing (first task).
- Produces: a bootable `MiniWsaApplication`; `GET /v1/ping` → `200 {"status":"ok"}`.

- [ ] **Step 1: Write the failing test**

`services/mini-wsa/src/test/java/com/akamai/wsa/interfaces/rest/PingControllerTest.java`
```java
package com.akamai.wsa.interfaces.rest;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(PingController.class)
class PingControllerTest {

    @Autowired
    MockMvc mockMvc;

    @Test
    void pingReturnsOk() throws Exception {
        mockMvc.perform(get("/v1/ping"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("ok"));
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `mvn -q -pl services/mini-wsa test`
Expected: FAIL — compilation error (`PingController` / classes don't exist yet).

- [ ] **Step 3: Write the root aggregator `pom.xml`**

`pom.xml`
```xml
<?xml version="1.0" encoding="UTF-8"?>
<project xmlns="http://maven.apache.org/POM/4.0.0"
         xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
         xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 http://maven.apache.org/xsd/maven-4.0.0.xsd">
    <modelVersion>4.0.0</modelVersion>

    <parent>
        <groupId>org.springframework.boot</groupId>
        <artifactId>spring-boot-starter-parent</artifactId>
        <version>3.3.5</version>
        <relativePath/>
    </parent>

    <groupId>com.akamai.wsa</groupId>
    <artifactId>mini-wsa-parent</artifactId>
    <version>0.1.0-SNAPSHOT</version>
    <packaging>pom</packaging>
    <name>Mini WSA Parent</name>

    <properties>
        <java.version>21</java.version>
        <maven.compiler.release>21</maven.compiler.release>
        <project.build.sourceEncoding>UTF-8</project.build.sourceEncoding>
    </properties>

    <modules>
        <module>services/mini-wsa</module>
    </modules>
</project>
```

- [ ] **Step 4: Write the `mini-wsa` module `pom.xml`**

`services/mini-wsa/pom.xml`
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

    <artifactId>mini-wsa</artifactId>
    <name>Mini WSA Service</name>

    <dependencies>
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-web</artifactId>
        </dependency>
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-validation</artifactId>
        </dependency>
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-actuator</artifactId>
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

- [ ] **Step 5: Write the application entrypoint**

`services/mini-wsa/src/main/java/com/akamai/wsa/MiniWsaApplication.java`
```java
package com.akamai.wsa;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
public class MiniWsaApplication {
    public static void main(String[] args) {
        SpringApplication.run(MiniWsaApplication.class, args);
    }
}
```

- [ ] **Step 6: Write the ping controller**

`services/mini-wsa/src/main/java/com/akamai/wsa/interfaces/rest/PingController.java`
```java
package com.akamai.wsa.interfaces.rest;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/v1")
class PingController {

    @GetMapping("/ping")
    Map<String, String> ping() {
        return Map.of("status", "ok");
    }
}
```

- [ ] **Step 7: Write `application.yml`**

`services/mini-wsa/src/main/resources/application.yml`
```yaml
server:
  port: 8080
spring:
  application:
    name: mini-wsa
management:
  endpoints:
    web:
      exposure:
        include: health,info
```

- [ ] **Step 8: Run the test to verify it passes**

Run: `mvn -q -pl services/mini-wsa test`
Expected: PASS (`PingControllerTest.pingReturnsOk`).

- [ ] **Step 9: Sanity dry-run — boot the app and hit it**

Run (terminal A): `mvn -q -pl services/mini-wsa spring-boot:run`
Run (terminal B):
```bash
curl -s localhost:8080/v1/ping        # => {"status":"ok"}
curl -s localhost:8080/actuator/health # => {"status":"UP"}
```
Expected: both respond as shown. Stop the app (Ctrl+C).

- [ ] **Step 10: Commit**

```bash
git add pom.xml services/mini-wsa/pom.xml services/mini-wsa/src \
        AGENTS.md README.md .gitignore docs
git rm -q --cached services/mini-wsa/src/main/java/com/akamai/wsa/interfaces/rest/.gitkeep 2>/dev/null || true
git commit -m "chore: scaffold multi-module build with bootable ping endpoint"
```

---

### Task 2: In-memory `EventRepository` adapter behind a domain port

**Files:**
- Create: `services/mini-wsa/src/main/java/com/akamai/wsa/domain/model/SecurityEvent.java`
- Create: `services/mini-wsa/src/main/java/com/akamai/wsa/domain/port/EventRepository.java`
- Create: `services/mini-wsa/src/main/java/com/akamai/wsa/infrastructure/persistence/inmemory/InMemoryEventRepository.java`
- Test: `services/mini-wsa/src/test/java/com/akamai/wsa/infrastructure/persistence/inmemory/InMemoryEventRepositoryTest.java`

**Interfaces:**
- Consumes: nothing from Task 1 (independent layer).
- Produces:
  - `record SecurityEvent(String eventId, int configId, java.time.Instant timestamp)` — the aggregate root **seed** (grows in later plans with `attackType`, `threatScore`, `receivedAt`, and the remaining DLR fields).
  - `interface EventRepository` with:
    - `void save(SecurityEvent event)`
    - `long count()`
    - `java.util.List<SecurityEvent> findByConfigId(int configId)`
  - `class InMemoryEventRepository implements EventRepository` — a Spring `@Repository`, thread-safe.

- [ ] **Step 1: Write the failing test**

`services/mini-wsa/src/test/java/com/akamai/wsa/infrastructure/persistence/inmemory/InMemoryEventRepositoryTest.java`
```java
package com.akamai.wsa.infrastructure.persistence.inmemory;

import com.akamai.wsa.domain.model.SecurityEvent;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class InMemoryEventRepositoryTest {

    private final InMemoryEventRepository repository = new InMemoryEventRepository();

    @Test
    void savedEventsAreCounted() {
        repository.save(new SecurityEvent("evt-1", 14227, Instant.parse("2026-05-20T14:32:10Z")));
        repository.save(new SecurityEvent("evt-2", 14227, Instant.parse("2026-05-20T14:33:10Z")));

        assertThat(repository.count()).isEqualTo(2);
    }

    @Test
    void findsByConfigId() {
        repository.save(new SecurityEvent("evt-1", 14227, Instant.parse("2026-05-20T14:32:10Z")));
        repository.save(new SecurityEvent("evt-2", 99999, Instant.parse("2026-05-20T14:33:10Z")));

        List<SecurityEvent> result = repository.findByConfigId(14227);

        assertThat(result).extracting(SecurityEvent::eventId).containsExactly("evt-1");
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `mvn -q -pl services/mini-wsa test -Dtest=InMemoryEventRepositoryTest`
Expected: FAIL — compilation error (types don't exist yet).

- [ ] **Step 3: Write the aggregate seed `SecurityEvent`**

`services/mini-wsa/src/main/java/com/akamai/wsa/domain/model/SecurityEvent.java`
```java
package com.akamai.wsa.domain.model;

import java.time.Instant;

// Aggregate root seed. Grows in later plans: attackType, threatScore,
// receivedAt, and the full DLR fields (rule, geoLocation, method, ...).
public record SecurityEvent(String eventId, int configId, Instant timestamp) {
}
```

- [ ] **Step 4: Write the domain port `EventRepository`**

`services/mini-wsa/src/main/java/com/akamai/wsa/domain/port/EventRepository.java`
```java
package com.akamai.wsa.domain.port;

import com.akamai.wsa.domain.model.SecurityEvent;

import java.util.List;

// Domain-owned outbound port. Any storage engine (in-memory, Postgres, Mongo,
// ...) is an adapter implementing this interface. The domain never depends on
// a concrete store.
public interface EventRepository {

    void save(SecurityEvent event);

    long count();

    List<SecurityEvent> findByConfigId(int configId);
}
```

- [ ] **Step 5: Write the in-memory adapter**

`services/mini-wsa/src/main/java/com/akamai/wsa/infrastructure/persistence/inmemory/InMemoryEventRepository.java`
```java
package com.akamai.wsa.infrastructure.persistence.inmemory;

import com.akamai.wsa.domain.model.SecurityEvent;
import com.akamai.wsa.domain.port.EventRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

@Repository
public class InMemoryEventRepository implements EventRepository {

    private final List<SecurityEvent> events = new CopyOnWriteArrayList<>();

    @Override
    public void save(SecurityEvent event) {
        events.add(event);
    }

    @Override
    public long count() {
        return events.size();
    }

    @Override
    public List<SecurityEvent> findByConfigId(int configId) {
        return events.stream()
                .filter(event -> event.configId() == configId)
                .toList();
    }
}
```

- [ ] **Step 6: Run the test to verify it passes**

Run: `mvn -q -pl services/mini-wsa test -Dtest=InMemoryEventRepositoryTest`
Expected: PASS (both tests).

- [ ] **Step 7: Commit**

```bash
git add services/mini-wsa/src/main/java/com/akamai/wsa/domain \
        services/mini-wsa/src/main/java/com/akamai/wsa/infrastructure \
        services/mini-wsa/src/test/java/com/akamai/wsa/infrastructure
git commit -m "feat: add in-memory event repository behind domain port"
```

---

### Task 3: Containerize the service and document build/run

**Files:**
- Create: `services/mini-wsa/Dockerfile`
- Create: `docker-compose.yml`
- Create: `.dockerignore`
- Modify: `README.md` (fill in Build & Run with verified commands)

**Interfaces:**
- Consumes: the bootable app from Task 1 (jar built by `spring-boot-maven-plugin`, listens on 8080).
- Produces: `docker compose up` serving `/v1/ping` on `localhost:8080`.

- [ ] **Step 1: Write the Dockerfile**

`services/mini-wsa/Dockerfile`
```dockerfile
# --- build stage ---
FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /build
COPY pom.xml .
COPY services/mini-wsa/pom.xml services/mini-wsa/pom.xml
COPY services/mini-wsa/src services/mini-wsa/src
RUN mvn -q -pl services/mini-wsa -am clean package -DskipTests

# --- runtime stage ---
FROM eclipse-temurin:21-jre
WORKDIR /app
COPY --from=build /build/services/mini-wsa/target/mini-wsa-*.jar app.jar
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "app.jar"]
```

- [ ] **Step 2: Write `.dockerignore`**

`.dockerignore`
```
**/target/
.idea/
.git/
*.iml
```

- [ ] **Step 3: Write `docker-compose.yml`**

`docker-compose.yml`
```yaml
services:
  mini-wsa:
    build:
      context: .
      dockerfile: services/mini-wsa/Dockerfile
    ports:
      - "8080:8080"
    # Storage services (Postgres/Mongo/...) get added here in the DB phase.
```

- [ ] **Step 4: Build and dry-run the container**

Run:
```bash
docker compose up --build -d
sleep 5
curl -s localhost:8080/v1/ping         # => {"status":"ok"}
docker compose down
```
Expected: `{"status":"ok"}`.

- [ ] **Step 5: Update the README Build & Run section**

Replace the "Build & run" section of `README.md` with:
````markdown
## Build & run

Prerequisites: JDK 21, Maven, Docker.

```bash
# build + run all tests
mvn -q clean verify

# run the service locally (http://localhost:8080)
mvn -q -pl services/mini-wsa spring-boot:run
curl -s localhost:8080/v1/ping          # {"status":"ok"}

# full stack via Docker
docker compose up --build
```
````

- [ ] **Step 6: Full verification**

Run: `mvn -q clean verify`
Expected: BUILD SUCCESS, all tests green.

- [ ] **Step 7: Commit and tag the skeleton**

```bash
git add services/mini-wsa/Dockerfile docker-compose.yml .dockerignore README.md
git commit -m "chore: containerize service and document build/run"
git tag v0.0-skeleton
```

---

## Self-Review

**Spec coverage (this plan = the "initial environment" ask):**
- Runnable Spring Boot app ✓ (Task 1)
- In-memory "cached DB" behind a swappable port ✓ (Task 2)
- Sanity/dry-run capability ✓ (Task 1 Step 9, Task 3 Step 4)
- docker-compose stack ✓ (Task 3)
- Clean/Onion layering with pure domain ✓ (Task 2: `domain` has no Spring imports; adapter in `infrastructure`)
- Tests present (unit + web slice) ✓
- *Out of scope by design (own plans later):* ingest/validate (Part 1), enrichment/scoring (Part 2), stats (Part 3), samples (Part 4), generator (Part 5), bonuses, real-DB adapters + load tests.

**Placeholder scan:** none — every step has concrete code/commands.

**Type consistency:** `SecurityEvent(eventId, configId, timestamp)` used identically in Task 2 test, model, and adapter; `EventRepository` methods (`save`/`count`/`findByConfigId`) match across port, adapter, and test. `spring-boot-maven-plugin` produces the `mini-wsa-*.jar` the Dockerfile copies.

> Note on `spring-boot-starter-parent` version 3.3.5: if Maven can't resolve it, bump to the latest available 3.3.x/3.4.x — it only affects the parent version line in the root `pom.xml`.
