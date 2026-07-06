# Part 1 — Ingestion API Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** `POST /v1/events/ingest` accepts a single event or an array, validates every event, stamps a server-side `receivedAt`, stores the events, and returns `201` with an accepted count (or `400` with per-item details).

**Architecture:** Inbound REST adapter (DTOs + Bean Validation) → `IngestSecurityEvents` application service → `EventStore` port. Part 1 uses **placeholder enrichment** (`attackType = AttackType.fromCategory(...)`, `threatScore = 0`) so the pipeline is end-to-end and shippable; **real scoring + repeat-offender land in Part 2 (`v0.2-enrichment`)**. Batch handling is **all-or-nothing**: any invalid event fails the whole request with a structured `400`.

**Tech Stack:** Java 21, Spring Boot 3.3.5 (web, validation, jackson-jsr310), JUnit 5 + MockMvc, AssertJ.

## Global Constraints

- **Java 21**, base package `com.akamai.wsa`, module `services/mini-wsa`.
- `domain` imports nothing from Spring/Jackson. DTOs + validation live in `interfaces`; wiring in `interfaces`/`infrastructure`.
- **Records** for all DTOs; immutable; **full descriptive parameter names** (no `v`/`e`/`req`).
- **All-or-nothing** batch semantics. Success `201`; validation failure `400` with `{"error":{"code","message","details":[...]}}`. **No `{success,data}` envelope.**
- Single-line logging with a `correlationId` (header `x-correlation-id`, generated if absent).
- `receivedAt` comes from an injected `java.time.Clock` (never `Instant.now()` inline) so it is testable.
- Local run port **8081** (`--server.port=8081`; 8080 is occupied on this machine).
- Conventional commits, imperative, ≤72 chars. Milestone tag `v0.1-ingestion` at the end.
- Reuse existing entities: `DataLogRecord`, `SecurityEvent`, `AttackType`, `ThreatScore`, `ClientIp`, `Rule`, `GeoLocation`, `Severity`, `Action`, `AttackCategory`; port `EventStore`; use case `IngestSecurityEvents` + `IngestionOutcome`; test helper `com.akamai.wsa.testsupport.SecurityEventFixtures`.

---

### Task 1: Ingestion application service (`IngestSecurityEvents` impl) + Clock

**Files:**
- Create: `services/mini-wsa/src/main/java/com/akamai/wsa/infrastructure/config/TimeConfig.java`
- Create: `services/mini-wsa/src/main/java/com/akamai/wsa/application/ingest/EventIngestionService.java`
- Test: `services/mini-wsa/src/test/java/com/akamai/wsa/application/ingest/EventIngestionServiceTest.java`

**Interfaces:**
- Consumes: `EventStore.saveAll(List<SecurityEvent>)`, `IngestSecurityEvents.ingest(List<DataLogRecord>)`, `IngestionOutcome`.
- Produces: `EventIngestionService implements IngestSecurityEvents` (Spring `@Service`); a `Clock` bean.

- [ ] **Step 1: Write the failing test**

`services/mini-wsa/src/test/java/com/akamai/wsa/application/ingest/EventIngestionServiceTest.java`
```java
package com.akamai.wsa.application.ingest;

import com.akamai.wsa.domain.model.AttackType;
import com.akamai.wsa.domain.model.DataLogRecord;
import com.akamai.wsa.infrastructure.persistence.inmemory.InMemoryEventStore;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;

import static com.akamai.wsa.testsupport.SecurityEventFixtures.dataLogRecord;
import static org.assertj.core.api.Assertions.assertThat;

class EventIngestionServiceTest {

    private static final Instant FIXED_NOW = Instant.parse("2026-05-20T14:32:10.512Z");

    private final InMemoryEventStore eventStore = new InMemoryEventStore();
    private final EventIngestionService service =
            new EventIngestionService(eventStore, Clock.fixed(FIXED_NOW, ZoneOffset.UTC));

    @Test
    void enrichesStampsAndStoresEveryEvent() {
        List<DataLogRecord> dataLogRecords = List.of(
                dataLogRecord("evt-1", 14227, "203.0.113.42", "/api/v1/login"),
                dataLogRecord("evt-2", 14227, "203.0.113.43", "/admin")
        );

        IngestionOutcome outcome = service.ingest(dataLogRecords);

        assertThat(outcome.acceptedCount()).isEqualTo(2);
        assertThat(eventStore.countAll()).isEqualTo(2);
        assertThat(outcome.storedEvents()).allSatisfy(securityEvent -> {
            assertThat(securityEvent.receivedAt()).isEqualTo(FIXED_NOW);
            assertThat(securityEvent.attackType()).isEqualTo(AttackType.SQL_COMMAND_INJECTION);
            assertThat(securityEvent.threatScore().value()).isZero(); // placeholder until Part 2
        });
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `mvn -q -pl services/mini-wsa test -Dtest=EventIngestionServiceTest`
Expected: FAIL — `EventIngestionService` does not exist (compilation error).

- [ ] **Step 3: Write the Clock bean**

`services/mini-wsa/src/main/java/com/akamai/wsa/infrastructure/config/TimeConfig.java`
```java
package com.akamai.wsa.infrastructure.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Clock;

@Configuration
public class TimeConfig {

    @Bean
    public Clock clock() {
        return Clock.systemUTC();
    }
}
```

- [ ] **Step 4: Write the ingestion service (placeholder enrichment)**

`services/mini-wsa/src/main/java/com/akamai/wsa/application/ingest/EventIngestionService.java`
```java
package com.akamai.wsa.application.ingest;

import com.akamai.wsa.domain.model.AttackType;
import com.akamai.wsa.domain.model.DataLogRecord;
import com.akamai.wsa.domain.model.SecurityEvent;
import com.akamai.wsa.domain.model.ThreatScore;
import com.akamai.wsa.domain.port.EventStore;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.Instant;
import java.util.List;

@Service
public class EventIngestionService implements IngestSecurityEvents {

    private static final Logger logger = LoggerFactory.getLogger(EventIngestionService.class);

    private final EventStore eventStore;
    private final Clock clock;

    public EventIngestionService(EventStore eventStore, Clock clock) {
        this.eventStore = eventStore;
        this.clock = clock;
    }

    @Override
    public IngestionOutcome ingest(List<DataLogRecord> dataLogRecords) {
        Instant receivedAt = clock.instant();
        // Part 1 placeholder enrichment: real threat scoring + repeat-offender arrive in Part 2.
        List<SecurityEvent> enrichedEvents = dataLogRecords.stream()
                .map(dataLogRecord -> new SecurityEvent(
                        dataLogRecord,
                        AttackType.fromCategory(dataLogRecord.rule().category()),
                        ThreatScore.ofCapped(0),
                        receivedAt))
                .toList();
        eventStore.saveAll(enrichedEvents);
        logger.info("EventIngestionService - ingest - accepted {} events", enrichedEvents.size());
        return new IngestionOutcome(enrichedEvents.size(), enrichedEvents);
    }
}
```

- [ ] **Step 5: Run the test to verify it passes**

Run: `mvn -q -pl services/mini-wsa test -Dtest=EventIngestionServiceTest`
Expected: PASS.

- [ ] **Step 6: Commit**

```bash
git add services/mini-wsa/src/main/java/com/akamai/wsa/infrastructure/config/TimeConfig.java \
        services/mini-wsa/src/main/java/com/akamai/wsa/application/ingest/EventIngestionService.java \
        services/mini-wsa/src/test/java/com/akamai/wsa/application/ingest/EventIngestionServiceTest.java
git commit -m "feat: add event ingestion service with placeholder enrichment"
```

---

### Task 2: Inbound DTOs, validation, and DTO→domain mapper

**Files:**
- Create: `services/mini-wsa/src/main/java/com/akamai/wsa/interfaces/rest/dto/RuleDto.java`
- Create: `services/mini-wsa/src/main/java/com/akamai/wsa/interfaces/rest/dto/GeoLocationDto.java`
- Create: `services/mini-wsa/src/main/java/com/akamai/wsa/interfaces/rest/dto/IngestEventRequest.java`
- Create: `services/mini-wsa/src/main/java/com/akamai/wsa/interfaces/rest/EventRequestMapper.java`
- Test: `services/mini-wsa/src/test/java/com/akamai/wsa/interfaces/rest/EventRequestMapperTest.java`

**Interfaces:**
- Consumes: `DataLogRecord`, `ClientIp`, `Rule`, `GeoLocation`, enums.
- Produces: `IngestEventRequest` (+ `RuleDto`, `GeoLocationDto`) with Jakarta constraints; `EventRequestMapper.toDataLogRecord(IngestEventRequest)`.

- [ ] **Step 1: Write the failing test**

`services/mini-wsa/src/test/java/com/akamai/wsa/interfaces/rest/EventRequestMapperTest.java`
```java
package com.akamai.wsa.interfaces.rest;

import com.akamai.wsa.domain.model.Action;
import com.akamai.wsa.domain.model.AttackCategory;
import com.akamai.wsa.domain.model.DataLogRecord;
import com.akamai.wsa.domain.model.Severity;
import com.akamai.wsa.interfaces.rest.dto.GeoLocationDto;
import com.akamai.wsa.interfaces.rest.dto.IngestEventRequest;
import com.akamai.wsa.interfaces.rest.dto.RuleDto;
import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

class EventRequestMapperTest {

    private final EventRequestMapper mapper = new EventRequestMapper();

    @Test
    void mapsRequestToDataLogRecord() {
        IngestEventRequest request = new IngestEventRequest(
                "evt-00132",
                Instant.parse("2026-05-20T14:32:10Z"),
                14227,
                "pol_web1",
                "203.0.113.42",
                "www.example.com",
                "/api/v1/login",
                "POST",
                403,
                "Mozilla/5.0",
                new RuleDto("950001", "SQL_INJECTION", "SQL Injection Attack Detected",
                        Severity.CRITICAL, AttackCategory.INJECTION),
                Action.DENY,
                new GeoLocationDto("CN", "Beijing"),
                1024L,
                256L);

        DataLogRecord dataLogRecord = mapper.toDataLogRecord(request);

        assertThat(dataLogRecord.eventId()).isEqualTo("evt-00132");
        assertThat(dataLogRecord.configId()).isEqualTo(14227);
        assertThat(dataLogRecord.clientIp().value()).isEqualTo("203.0.113.42");
        assertThat(dataLogRecord.rule().category()).isEqualTo(AttackCategory.INJECTION);
        assertThat(dataLogRecord.action()).isEqualTo(Action.DENY);
        assertThat(dataLogRecord.path()).isEqualTo("/api/v1/login");
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `mvn -q -pl services/mini-wsa test -Dtest=EventRequestMapperTest`
Expected: FAIL — DTO/mapper types do not exist.

- [ ] **Step 3: Write `RuleDto`**

`services/mini-wsa/src/main/java/com/akamai/wsa/interfaces/rest/dto/RuleDto.java`
```java
package com.akamai.wsa.interfaces.rest.dto;

import com.akamai.wsa.domain.model.AttackCategory;
import com.akamai.wsa.domain.model.Severity;
import jakarta.validation.constraints.NotNull;

public record RuleDto(
        String id,
        String name,
        String message,
        @NotNull(message = "rule.severity is required") Severity severity,
        @NotNull(message = "rule.category is required") AttackCategory category
) {
}
```

- [ ] **Step 4: Write `GeoLocationDto`**

`services/mini-wsa/src/main/java/com/akamai/wsa/interfaces/rest/dto/GeoLocationDto.java`
```java
package com.akamai.wsa.interfaces.rest.dto;

public record GeoLocationDto(String country, String city) {
}
```

- [ ] **Step 5: Write `IngestEventRequest`**

`services/mini-wsa/src/main/java/com/akamai/wsa/interfaces/rest/dto/IngestEventRequest.java`
```java
package com.akamai.wsa.interfaces.rest.dto;

import com.akamai.wsa.domain.model.Action;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.time.Instant;

public record IngestEventRequest(
        @NotBlank(message = "eventId is required") String eventId,
        @NotNull(message = "timestamp is required") Instant timestamp,
        @NotNull(message = "configId is required") Integer configId,
        String policyId,
        @NotBlank(message = "clientIp is required") String clientIp,
        String hostname,
        @NotBlank(message = "path is required") String path,
        String method,
        Integer statusCode,
        String userAgent,
        @NotNull(message = "rule is required") @Valid RuleDto rule,
        @NotNull(message = "action is required") Action action,
        @Valid GeoLocationDto geoLocation,
        Long requestSize,
        Long responseSize
) {
}
```

- [ ] **Step 6: Write `EventRequestMapper`**

`services/mini-wsa/src/main/java/com/akamai/wsa/interfaces/rest/EventRequestMapper.java`
```java
package com.akamai.wsa.interfaces.rest;

import com.akamai.wsa.domain.model.ClientIp;
import com.akamai.wsa.domain.model.DataLogRecord;
import com.akamai.wsa.domain.model.GeoLocation;
import com.akamai.wsa.domain.model.Rule;
import com.akamai.wsa.interfaces.rest.dto.GeoLocationDto;
import com.akamai.wsa.interfaces.rest.dto.IngestEventRequest;
import org.springframework.stereotype.Component;

@Component
public class EventRequestMapper {

    public DataLogRecord toDataLogRecord(IngestEventRequest ingestEventRequest) {
        return new DataLogRecord(
                ingestEventRequest.eventId(),
                ingestEventRequest.timestamp(),
                ingestEventRequest.configId(),
                ingestEventRequest.policyId(),
                new ClientIp(ingestEventRequest.clientIp()),
                ingestEventRequest.hostname(),
                ingestEventRequest.path(),
                ingestEventRequest.method(),
                ingestEventRequest.statusCode() == null ? 0 : ingestEventRequest.statusCode(),
                ingestEventRequest.userAgent(),
                new Rule(
                        ingestEventRequest.rule().id(),
                        ingestEventRequest.rule().name(),
                        ingestEventRequest.rule().message(),
                        ingestEventRequest.rule().severity(),
                        ingestEventRequest.rule().category()),
                ingestEventRequest.action(),
                toGeoLocation(ingestEventRequest.geoLocation()),
                ingestEventRequest.requestSize() == null ? 0L : ingestEventRequest.requestSize(),
                ingestEventRequest.responseSize() == null ? 0L : ingestEventRequest.responseSize());
    }

    private GeoLocation toGeoLocation(GeoLocationDto geoLocationDto) {
        if (geoLocationDto == null) {
            return null;
        }
        return new GeoLocation(geoLocationDto.country(), geoLocationDto.city());
    }
}
```

- [ ] **Step 7: Run the test to verify it passes**

Run: `mvn -q -pl services/mini-wsa test -Dtest=EventRequestMapperTest`
Expected: PASS.

- [ ] **Step 8: Commit**

```bash
git add services/mini-wsa/src/main/java/com/akamai/wsa/interfaces/rest/dto/ \
        services/mini-wsa/src/main/java/com/akamai/wsa/interfaces/rest/EventRequestMapper.java \
        services/mini-wsa/src/test/java/com/akamai/wsa/interfaces/rest/EventRequestMapperTest.java
git commit -m "feat: add ingestion request DTOs with validation and domain mapper"
```

---

### Task 3: Ingest controller (single or array), all-or-nothing validation, error advice

**Files:**
- Create: `services/mini-wsa/src/main/java/com/akamai/wsa/interfaces/rest/dto/IngestResponse.java`
- Create: `services/mini-wsa/src/main/java/com/akamai/wsa/interfaces/error/ErrorDetail.java`
- Create: `services/mini-wsa/src/main/java/com/akamai/wsa/interfaces/error/ApiErrorResponse.java`
- Create: `services/mini-wsa/src/main/java/com/akamai/wsa/interfaces/error/InvalidIngestionRequestException.java`
- Create: `services/mini-wsa/src/main/java/com/akamai/wsa/interfaces/error/ApiExceptionHandler.java`
- Create: `services/mini-wsa/src/main/java/com/akamai/wsa/interfaces/rest/IngestController.java`
- Test: `services/mini-wsa/src/test/java/com/akamai/wsa/interfaces/rest/IngestControllerTest.java`

**Interfaces:**
- Consumes: `IngestSecurityEvents.ingest(...)`, `EventRequestMapper`, `jakarta.validation.Validator`, `ObjectMapper`.
- Produces: `POST /v1/events/ingest` → `201 {"acceptedCount": N}` or `400 {"error":{"code","message","details":[{index,field,message}]}}`. Accepts a single JSON object or a JSON array.

- [ ] **Step 1: Write the failing test**

`services/mini-wsa/src/test/java/com/akamai/wsa/interfaces/rest/IngestControllerTest.java`
```java
package com.akamai.wsa.interfaces.rest;

import com.akamai.wsa.application.ingest.IngestSecurityEvents;
import com.akamai.wsa.application.ingest.IngestionOutcome;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = IngestController.class)
@org.springframework.context.annotation.Import({EventRequestMapper.class,
        com.akamai.wsa.interfaces.error.ApiExceptionHandler.class})
class IngestControllerTest {

    @Autowired
    MockMvc mockMvc;

    @MockBean
    IngestSecurityEvents ingestSecurityEvents;

    private static final String VALID_EVENT = """
            {
              "eventId": "evt-00132",
              "timestamp": "2026-05-20T14:32:10Z",
              "configId": 14227,
              "clientIp": "203.0.113.42",
              "path": "/api/v1/login",
              "rule": { "severity": "CRITICAL", "category": "INJECTION" },
              "action": "DENY"
            }
            """;

    @Test
    void acceptsSingleEvent() throws Exception {
        when(ingestSecurityEvents.ingest(any())).thenReturn(new IngestionOutcome(1, List.of()));

        mockMvc.perform(post("/v1/events/ingest").contentType(MediaType.APPLICATION_JSON).content(VALID_EVENT))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.acceptedCount").value(1));
    }

    @Test
    void acceptsBatchArray() throws Exception {
        when(ingestSecurityEvents.ingest(any())).thenReturn(new IngestionOutcome(2, List.of()));
        String batch = "[" + VALID_EVENT + "," + VALID_EVENT + "]";

        mockMvc.perform(post("/v1/events/ingest").contentType(MediaType.APPLICATION_JSON).content(batch))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.acceptedCount").value(2));

        ArgumentCaptor<List> captor = ArgumentCaptor.forClass(List.class);
        org.mockito.Mockito.verify(ingestSecurityEvents).ingest(captor.capture());
        assertThat(captor.getValue()).hasSize(2);
    }

    @Test
    void rejectsMissingRequiredField() throws Exception {
        String missingEventId = VALID_EVENT.replace("\"eventId\": \"evt-00132\",", "");

        mockMvc.perform(post("/v1/events/ingest").contentType(MediaType.APPLICATION_JSON).content(missingEventId))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.error.details").isArray());
    }

    @Test
    void rejectsInvalidEnum() throws Exception {
        String badCategory = VALID_EVENT.replace("\"category\": \"INJECTION\"", "\"category\": \"NOT_A_CATEGORY\"");

        mockMvc.perform(post("/v1/events/ingest").contentType(MediaType.APPLICATION_JSON).content(badCategory))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("VALIDATION_ERROR"));
    }

    @Test
    void rejectsBadTimestamp() throws Exception {
        String badTimestamp = VALID_EVENT.replace("2026-05-20T14:32:10Z", "not-a-timestamp");

        mockMvc.perform(post("/v1/events/ingest").contentType(MediaType.APPLICATION_JSON).content(badTimestamp))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("VALIDATION_ERROR"));
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `mvn -q -pl services/mini-wsa test -Dtest=IngestControllerTest`
Expected: FAIL — controller, response, error types do not exist.

- [ ] **Step 3: Write the response + error records**

`services/mini-wsa/src/main/java/com/akamai/wsa/interfaces/rest/dto/IngestResponse.java`
```java
package com.akamai.wsa.interfaces.rest.dto;

public record IngestResponse(int acceptedCount) {
}
```

`services/mini-wsa/src/main/java/com/akamai/wsa/interfaces/error/ErrorDetail.java`
```java
package com.akamai.wsa.interfaces.error;

public record ErrorDetail(int index, String field, String message) {
}
```

`services/mini-wsa/src/main/java/com/akamai/wsa/interfaces/error/ApiErrorResponse.java`
```java
package com.akamai.wsa.interfaces.error;

import java.util.List;

public record ApiErrorResponse(ErrorBody error) {

    public record ErrorBody(String code, String message, List<ErrorDetail> details) {
    }

    public static ApiErrorResponse validation(String message, List<ErrorDetail> details) {
        return new ApiErrorResponse(new ErrorBody("VALIDATION_ERROR", message, details));
    }
}
```

- [ ] **Step 4: Write the domain-ish validation exception**

`services/mini-wsa/src/main/java/com/akamai/wsa/interfaces/error/InvalidIngestionRequestException.java`
```java
package com.akamai.wsa.interfaces.error;

import java.util.List;

public class InvalidIngestionRequestException extends RuntimeException {

    private final transient List<ErrorDetail> details;

    public InvalidIngestionRequestException(List<ErrorDetail> details) {
        super("ingestion request failed validation");
        this.details = details;
    }

    public List<ErrorDetail> details() {
        return details;
    }
}
```

- [ ] **Step 5: Write the exception handler (`@RestControllerAdvice`)**

`services/mini-wsa/src/main/java/com/akamai/wsa/interfaces/error/ApiExceptionHandler.java`
```java
package com.akamai.wsa.interfaces.error;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.List;

@RestControllerAdvice
public class ApiExceptionHandler {

    @ExceptionHandler(InvalidIngestionRequestException.class)
    public ResponseEntity<ApiErrorResponse> handleInvalidIngestion(
            InvalidIngestionRequestException invalidIngestionRequestException) {
        return ResponseEntity.badRequest()
                .body(ApiErrorResponse.validation("one or more events are invalid",
                        invalidIngestionRequestException.details()));
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ApiErrorResponse> handleUnreadable(
            HttpMessageNotReadableException httpMessageNotReadableException) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(ApiErrorResponse.validation("request body is not valid JSON",
                        List.of(new ErrorDetail(0, "body", "malformed or unreadable JSON"))));
    }
}
```

- [ ] **Step 6: Write the controller (single-or-array + all-or-nothing validation)**

`services/mini-wsa/src/main/java/com/akamai/wsa/interfaces/rest/IngestController.java`
```java
package com.akamai.wsa.interfaces.rest;

import com.akamai.wsa.application.ingest.IngestSecurityEvents;
import com.akamai.wsa.application.ingest.IngestionOutcome;
import com.akamai.wsa.domain.model.DataLogRecord;
import com.akamai.wsa.interfaces.error.ErrorDetail;
import com.akamai.wsa.interfaces.error.InvalidIngestionRequestException;
import com.akamai.wsa.interfaces.rest.dto.IngestEventRequest;
import com.akamai.wsa.interfaces.rest.dto.IngestResponse;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

@RestController
@RequestMapping("/v1/events")
public class IngestController {

    private static final Logger logger = LoggerFactory.getLogger(IngestController.class);

    private final IngestSecurityEvents ingestSecurityEvents;
    private final EventRequestMapper eventRequestMapper;
    private final Validator validator;
    private final ObjectMapper objectMapper;

    public IngestController(IngestSecurityEvents ingestSecurityEvents,
                            EventRequestMapper eventRequestMapper,
                            Validator validator,
                            ObjectMapper objectMapper) {
        this.ingestSecurityEvents = ingestSecurityEvents;
        this.eventRequestMapper = eventRequestMapper;
        this.validator = validator;
        this.objectMapper = objectMapper;
    }

    @PostMapping("/ingest")
    public ResponseEntity<IngestResponse> ingest(@RequestBody JsonNode requestBody) {
        List<JsonNode> eventNodes = requestBody.isArray()
                ? toList(requestBody)
                : List.of(requestBody);

        List<ErrorDetail> validationErrors = new ArrayList<>();
        List<IngestEventRequest> parsedRequests = new ArrayList<>();
        for (int index = 0; index < eventNodes.size(); index++) {
            try {
                IngestEventRequest ingestEventRequest =
                        objectMapper.treeToValue(eventNodes.get(index), IngestEventRequest.class);
                Set<ConstraintViolation<IngestEventRequest>> violations = validator.validate(ingestEventRequest);
                if (violations.isEmpty()) {
                    parsedRequests.add(ingestEventRequest);
                } else {
                    for (ConstraintViolation<IngestEventRequest> violation : violations) {
                        validationErrors.add(new ErrorDetail(index,
                                violation.getPropertyPath().toString(), violation.getMessage()));
                    }
                }
            } catch (Exception parseFailure) {
                validationErrors.add(new ErrorDetail(index, "body", "unparseable event: " + rootMessage(parseFailure)));
            }
        }

        if (!validationErrors.isEmpty()) {
            logger.info("IngestController - ingest - rejected {} of {} events",
                    validationErrors.size(), eventNodes.size());
            throw new InvalidIngestionRequestException(validationErrors);
        }

        List<DataLogRecord> dataLogRecords = parsedRequests.stream()
                .map(eventRequestMapper::toDataLogRecord)
                .toList();
        IngestionOutcome outcome = ingestSecurityEvents.ingest(dataLogRecords);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(new IngestResponse(outcome.acceptedCount()));
    }

    private static List<JsonNode> toList(JsonNode arrayNode) {
        List<JsonNode> nodes = new ArrayList<>();
        arrayNode.forEach(nodes::add);
        return nodes;
    }

    private static String rootMessage(Throwable throwable) {
        Throwable current = throwable;
        while (current.getCause() != null) {
            current = current.getCause();
        }
        return current.getMessage();
    }
}
```

- [ ] **Step 7: Run the test to verify it passes**

Run: `mvn -q -pl services/mini-wsa test -Dtest=IngestControllerTest`
Expected: PASS (all five cases).

- [ ] **Step 8: Full build + manual dry run**

Run: `mvn -q clean verify`
Expected: BUILD SUCCESS.

Then boot and exercise it:
```bash
mvn -q -pl services/mini-wsa spring-boot:run -Dspring-boot.run.arguments=--server.port=8081 &
curl -s -XPOST localhost:8081/v1/events/ingest -H 'Content-Type: application/json' -d '{
  "eventId":"evt-1","timestamp":"2026-05-20T14:32:10Z","configId":14227,
  "clientIp":"203.0.113.42","path":"/api/v1/login",
  "rule":{"severity":"CRITICAL","category":"INJECTION"},"action":"DENY"}'
# => {"acceptedCount":1}
curl -s -XPOST localhost:8081/v1/events/ingest -H 'Content-Type: application/json' -d '{"configId":14227}'
# => 400 {"error":{"code":"VALIDATION_ERROR",...,"details":[...]}}
```

- [ ] **Step 9: Commit and tag**

```bash
git add services/mini-wsa/src/main/java/com/akamai/wsa/interfaces/
git add services/mini-wsa/src/test/java/com/akamai/wsa/interfaces/rest/IngestControllerTest.java
git commit -m "feat: add ingestion endpoint with single/batch validation and error handling"
git tag v0.1-ingestion
```

---

## Self-Review

**Spec coverage (Part 1):**
- `POST /v1/events/ingest` single or array ✓ (Task 3, `JsonNode` normalization).
- Validate required fields, enum values, ISO-8601 timestamp ✓ (Task 2 DTO constraints + Task 3 per-item parse/validate).
- `201` success / `400` with details ✓ (Task 3 + error advice).
- Server-side `receivedAt` ✓ (Task 1, injected `Clock`).
- Stored via `EventStore` ✓ (Task 1).

**Placeholder scan:** none — every step has complete code/commands. The `threatScore = 0` is a deliberate, documented Part-1 placeholder (real scoring is Part 2), not an unfinished step.

**Type consistency:** `EventIngestionService(EventStore, Clock)` matches its test; `IngestSecurityEvents.ingest(List<DataLogRecord>)` and `IngestionOutcome(acceptedCount, storedEvents)` match the existing interfaces; `EventRequestMapper.toDataLogRecord(IngestEventRequest)` matches its test and the `DataLogRecord` 15-arg constructor; controller returns `IngestResponse(acceptedCount)` matching the `$.acceptedCount` assertions; error body `{"error":{"code","message","details"}}` matches the `$.error.code` / `$.error.details` assertions.

> Note: `@WebMvcTest` auto-provides `ObjectMapper` and `Validator` beans; the test `@Import`s `EventRequestMapper` and `ApiExceptionHandler`. The `Clock`/`EventStore`/service are not loaded in the web slice (the use case is `@MockBean`), so no extra config is needed there.
