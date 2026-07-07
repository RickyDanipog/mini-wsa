# Gateway Service Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Build the `gateway` service — the write-path entry point. It accepts security events over REST (single or batch), validates them, transforms them into the shared `RawEventMessage` contract, and publishes them (enveloped, correlation-tagged) to the Kafka topic `events.raw`. It stores nothing.

**Architecture:** Hexagonal inside the service. REST controller + DTOs + validation live in `interfaces/rest`; the outbound `RawEventPublisher` port lives in the domain/application boundary and is implemented by a Kafka adapter in `infrastructure`. See `docs/03-sdd.md` v2 §3/§5.1 and the v2 restructure plan (`2026-07-07-v2-restructure.md`). Reuses the ingestion/validation logic from `2026-07-07-part1-ingestion.md`, but **publishes instead of storing**.

**Tech Stack:** Java 21, Spring Boot 3.3.5 (web, validation, actuator, `spring-kafka`), the `contracts` module, JUnit 5 + MockMvc + `@EmbeddedKafka`.

## Global Constraints

- Base package `com.akamai.wsa.gateway`. Service port `8081`.
- Depends on the `contracts` module for `RawEventMessage`, `RuleMessage`, `GeoLocationMessage`, `MessageEnvelope`, and enums `AttackCategory`/`Action`/`Severity`.
- Topic name from config key `wsa.kafka.topics.events-raw` (default `events.raw`) — never a literal in code. Messages keyed by `clientIp`.
- Every published message is wrapped in a `MessageEnvelope` carrying `correlationId` (from `x-correlation-id` header, else generated), `occurredAt` (from an injected `java.time.Clock`), and `version`.
- **All-or-nothing** batch validation: any invalid event → `400 {error:{code,message,details[]}}`; all valid → `201 {acceptedCount}`. No `{success,data}` envelope on responses.
- Records for DTOs, immutability, intention-revealing names, FULL descriptive parameter names (no `v`/`e`/`req`). Single-line structured logging with `correlationId` as the third arg. Conventional commits. `domain` imports no Spring/Kafka.
- Assumes the gateway module scaffold (pom, `GatewayApplication`, `application.yml`, `/v1/ping`) already exists from the restructure phase.

---

### Task 1: Map a validated request DTO to the `RawEventMessage` contract

**Files:**
- Create: `services/gateway/src/main/java/com/akamai/wsa/gateway/interfaces/rest/dto/IngestEventRequest.java`
- Create: `.../interfaces/rest/dto/RuleDto.java`, `.../dto/GeoLocationDto.java`
- Create: `.../application/EventRequestMapper.java`
- Test: `.../test/java/com/akamai/wsa/gateway/application/EventRequestMapperTest.java`

**Interfaces:**
- Consumes: `contracts` records (`RawEventMessage`, `RuleMessage`, `GeoLocationMessage`, enums).
- Produces: `EventRequestMapper.toRawEventMessage(IngestEventRequest ingestEventRequest) : RawEventMessage` — pure, no framework.

- [ ] **Step 1: Write the failing test**

`EventRequestMapperTest.java`
```java
package com.akamai.wsa.gateway.application;

import com.akamai.wsa.contracts.AttackCategory;
import com.akamai.wsa.contracts.Action;
import com.akamai.wsa.contracts.RawEventMessage;
import com.akamai.wsa.contracts.Severity;
import com.akamai.wsa.gateway.interfaces.rest.dto.GeoLocationDto;
import com.akamai.wsa.gateway.interfaces.rest.dto.IngestEventRequest;
import com.akamai.wsa.gateway.interfaces.rest.dto.RuleDto;
import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

class EventRequestMapperTest {

    private final EventRequestMapper mapper = new EventRequestMapper();

    @Test
    void mapsEveryFieldOntoTheContract() {
        IngestEventRequest ingestEventRequest = new IngestEventRequest(
                "evt-00132", Instant.parse("2026-05-20T14:32:10Z"), 14227, "pol_web1",
                "203.0.113.42", "www.example.com", "/api/v1/login", "POST", 403, "Mozilla/5.0",
                new RuleDto("950001", "SQL_INJECTION", "SQL Injection Attack Detected",
                        Severity.CRITICAL, AttackCategory.INJECTION),
                Action.DENY, new GeoLocationDto("CN", "Beijing"), 1024, 256);

        RawEventMessage rawEventMessage = mapper.toRawEventMessage(ingestEventRequest);

        assertThat(rawEventMessage.eventId()).isEqualTo("evt-00132");
        assertThat(rawEventMessage.clientIp()).isEqualTo("203.0.113.42");
        assertThat(rawEventMessage.rule().category()).isEqualTo(AttackCategory.INJECTION);
        assertThat(rawEventMessage.action()).isEqualTo(Action.DENY);
        assertThat(rawEventMessage.geoLocation().country()).isEqualTo("CN");
        assertThat(rawEventMessage.responseSize()).isEqualTo(256);
    }
}
```

- [ ] **Step 2: Run — expect FAIL** (`mvn -q -pl services/gateway test -Dtest=EventRequestMapperTest`).

- [ ] **Step 3: Implement the DTOs (with Bean Validation) and the mapper**

`RuleDto.java`
```java
package com.akamai.wsa.gateway.interfaces.rest.dto;

import com.akamai.wsa.contracts.AttackCategory;
import com.akamai.wsa.contracts.Severity;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record RuleDto(
        @NotBlank String id,
        @NotBlank String name,
        String message,
        @NotNull Severity severity,
        @NotNull AttackCategory category
) {
}
```

`GeoLocationDto.java`
```java
package com.akamai.wsa.gateway.interfaces.rest.dto;

public record GeoLocationDto(String country, String city) {
}
```

`IngestEventRequest.java`
```java
package com.akamai.wsa.gateway.interfaces.rest.dto;

import com.akamai.wsa.contracts.Action;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.time.Instant;

public record IngestEventRequest(
        @NotBlank String eventId,
        @NotNull Instant timestamp,
        int configId,
        String policyId,
        @NotBlank String clientIp,
        String hostname,
        @NotBlank String path,
        String method,
        int statusCode,
        String userAgent,
        @Valid @NotNull RuleDto rule,
        @NotNull Action action,
        @Valid GeoLocationDto geoLocation,
        long requestSize,
        long responseSize
) {
}
```

`EventRequestMapper.java`
```java
package com.akamai.wsa.gateway.application;

import com.akamai.wsa.contracts.GeoLocationMessage;
import com.akamai.wsa.contracts.RawEventMessage;
import com.akamai.wsa.contracts.RuleMessage;
import com.akamai.wsa.gateway.interfaces.rest.dto.GeoLocationDto;
import com.akamai.wsa.gateway.interfaces.rest.dto.IngestEventRequest;
import com.akamai.wsa.gateway.interfaces.rest.dto.RuleDto;

/** Pure DTO → contract mapper. No framework, no I/O. */
public class EventRequestMapper {

    public RawEventMessage toRawEventMessage(IngestEventRequest ingestEventRequest) {
        return new RawEventMessage(
                ingestEventRequest.eventId(),
                ingestEventRequest.timestamp(),
                ingestEventRequest.configId(),
                ingestEventRequest.policyId(),
                ingestEventRequest.clientIp(),
                ingestEventRequest.hostname(),
                ingestEventRequest.path(),
                ingestEventRequest.method(),
                ingestEventRequest.statusCode(),
                ingestEventRequest.userAgent(),
                toRuleMessage(ingestEventRequest.rule()),
                ingestEventRequest.action(),
                toGeoLocationMessage(ingestEventRequest.geoLocation()),
                ingestEventRequest.requestSize(),
                ingestEventRequest.responseSize());
    }

    private RuleMessage toRuleMessage(RuleDto ruleDto) {
        return new RuleMessage(ruleDto.id(), ruleDto.name(), ruleDto.message(),
                ruleDto.severity(), ruleDto.category());
    }

    private GeoLocationMessage toGeoLocationMessage(GeoLocationDto geoLocationDto) {
        if (geoLocationDto == null) {
            return null;
        }
        return new GeoLocationMessage(geoLocationDto.country(), geoLocationDto.city());
    }
}
```

- [ ] **Step 4: Run — expect PASS.**
- [ ] **Step 5: Commit** — `feat(gateway): add ingest DTOs and contract mapper`.

---

### Task 2: Publish enveloped raw events to Kafka (outbound port + adapter)

**Files:**
- Create: `services/gateway/src/main/java/com/akamai/wsa/gateway/application/RawEventPublisher.java` (port)
- Create: `.../infrastructure/kafka/KafkaRawEventPublisher.java` (adapter)
- Create: `.../infrastructure/config/KafkaTopics.java` (`@ConfigurationProperties`)
- Modify: `.../src/main/resources/application.yml` (topic + producer serialization)
- Test: `.../test/java/com/akamai/wsa/gateway/infrastructure/kafka/KafkaRawEventPublisherIT.java` (`@EmbeddedKafka`)

**Interfaces:**
- Produces: `RawEventPublisher.publish(MessageEnvelope<RawEventMessage> envelope) : void` (keys by `envelope.payload().clientIp()` internally).

- [ ] **Step 1: Write the failing integration test**

```java
package com.akamai.wsa.gateway.infrastructure.kafka;

import com.akamai.wsa.contracts.MessageEnvelope;
import com.akamai.wsa.contracts.RawEventMessage;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.kafka.core.ConsumerFactory;
import org.springframework.kafka.test.context.EmbeddedKafka;
import org.springframework.kafka.test.utils.KafkaTestUtils;

import java.time.Instant;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

@SpringBootTest
@EmbeddedKafka(partitions = 1, topics = "events.raw")
class KafkaRawEventPublisherIT {

    @Autowired
    RawEventPublisherProbeSupport support; // helper wiring the publisher + a test consumer (defined in the test config below)

    @Test
    void publishesEnvelopedMessageToEventsRaw() {
        MessageEnvelope<RawEventMessage> envelope = support.sampleEnvelope("corr-123", "203.0.113.42");

        support.publisher().publish(envelope);

        await().untilAsserted(() -> {
            var received = support.pollOne();
            assertThat(received.correlationId()).isEqualTo("corr-123");
            assertThat(received.payload().clientIp()).isEqualTo("203.0.113.42");
        });
    }
}
```

> Note for the implementer: create a small `@TestConfiguration` (`RawEventPublisherProbeSupport`) that autowires the real `RawEventPublisher` and builds a raw `KafkaConsumer` on `events.raw` via `ConsumerFactory`/`KafkaTestUtils`, deserializing the JSON envelope. Keep it in test scope. Add `org.awaitility:awaitility` (test) and `org.springframework.kafka:spring-kafka-test` to the gateway pom.

- [ ] **Step 2: Run — expect FAIL** (publisher/port missing).

- [ ] **Step 3: Implement the port, config, and adapter**

`RawEventPublisher.java`
```java
package com.akamai.wsa.gateway.application;

import com.akamai.wsa.contracts.MessageEnvelope;
import com.akamai.wsa.contracts.RawEventMessage;

/** Outbound port: hand a validated, enveloped raw event to the pipeline. */
public interface RawEventPublisher {
    void publish(MessageEnvelope<RawEventMessage> messageEnvelope);
}
```

`KafkaTopics.java`
```java
package com.akamai.wsa.gateway.infrastructure.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "wsa.kafka.topics")
public record KafkaTopics(String eventsRaw) {
}
```

`KafkaRawEventPublisher.java`
```java
package com.akamai.wsa.gateway.infrastructure.kafka;

import com.akamai.wsa.contracts.MessageEnvelope;
import com.akamai.wsa.contracts.RawEventMessage;
import com.akamai.wsa.gateway.application.RawEventPublisher;
import com.akamai.wsa.gateway.infrastructure.config.KafkaTopics;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

@Component
public class KafkaRawEventPublisher implements RawEventPublisher {

    private final KafkaTemplate<String, MessageEnvelope<RawEventMessage>> kafkaTemplate;
    private final KafkaTopics kafkaTopics;

    public KafkaRawEventPublisher(
            KafkaTemplate<String, MessageEnvelope<RawEventMessage>> kafkaTemplate,
            KafkaTopics kafkaTopics) {
        this.kafkaTemplate = kafkaTemplate;
        this.kafkaTopics = kafkaTopics;
    }

    @Override
    public void publish(MessageEnvelope<RawEventMessage> messageEnvelope) {
        String partitionKey = messageEnvelope.payload().clientIp();
        kafkaTemplate.send(kafkaTopics.eventsRaw(), partitionKey, messageEnvelope);
    }
}
```

`application.yml` (add)
```yaml
wsa:
  kafka:
    topics:
      events-raw: events.raw
spring:
  kafka:
    producer:
      key-serializer: org.apache.kafka.common.serialization.StringSerializer
      value-serializer: org.springframework.kafka.support.serializer.JsonSerializer
```
Enable the properties record: add `@ConfigurationPropertiesScan` to `GatewayApplication` (or `@EnableConfigurationProperties(KafkaTopics.class)`).

- [ ] **Step 4: Run — expect PASS.**
- [ ] **Step 5: Commit** — `feat(gateway): publish enveloped raw events to Kafka`.

---

### Task 3: Ingest endpoint — single/array, all-or-nothing validation, 201/400

**Files:**
- Create: `.../interfaces/rest/IngestController.java`
- Create: `.../interfaces/rest/IngestResponse.java` (`record IngestResponse(int acceptedCount)`)
- Create: `.../interfaces/rest/error/ApiError.java`, `.../error/ApiErrorBody.java`, `.../error/GatewayExceptionHandler.java` (`@RestControllerAdvice`)
- Create: `.../application/CorrelationId.java` (mint/resolve helper) — optional small helper
- Test: `.../test/java/com/akamai/wsa/gateway/interfaces/rest/IngestControllerTest.java` (`@WebMvcTest`)

**Interfaces:**
- Consumes: `EventRequestMapper`, `RawEventPublisher` (mocked in the web test), `jakarta.validation.Validator`, `Clock`.
- Produces: `POST /v1/events/ingest` → `201 {acceptedCount}` or `400 {error:{code,message,details:[{field,message}]}}`.

- [ ] **Step 1: Write the failing `@WebMvcTest`**

```java
package com.akamai.wsa.gateway.interfaces.rest;

import com.akamai.wsa.gateway.application.RawEventPublisher;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(IngestController.class)
class IngestControllerTest {

    @Autowired
    MockMvc mockMvc;

    @MockBean
    RawEventPublisher rawEventPublisher;

    private static final String VALID_EVENT = """
        {"eventId":"evt-1","timestamp":"2026-05-20T14:32:10Z","configId":14227,"clientIp":"203.0.113.42",
         "path":"/api/v1/login","statusCode":403,
         "rule":{"id":"950001","name":"SQL_INJECTION","message":"m","severity":"CRITICAL","category":"INJECTION"},
         "action":"DENY","requestSize":1024,"responseSize":256}""";

    @Test
    void acceptsASingleValidEvent() throws Exception {
        mockMvc.perform(post("/v1/events/ingest").contentType(MediaType.APPLICATION_JSON).content(VALID_EVENT))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.acceptedCount").value(1));
        verify(rawEventPublisher).publish(org.mockito.ArgumentMatchers.any());
    }

    @Test
    void acceptsAValidBatch() throws Exception {
        mockMvc.perform(post("/v1/events/ingest").contentType(MediaType.APPLICATION_JSON)
                        .content("[" + VALID_EVENT + "," + VALID_EVENT + "]"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.acceptedCount").value(2));
    }

    @Test
    void rejectsBatchWhenAnyEventInvalid_allOrNothing() throws Exception {
        String missingClientIp = VALID_EVENT.replace("\"clientIp\":\"203.0.113.42\",", "");
        mockMvc.perform(post("/v1/events/ingest").contentType(MediaType.APPLICATION_JSON)
                        .content("[" + VALID_EVENT + "," + missingClientIp + "]"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.error.details").isArray());
    }

    @Test
    void rejectsInvalidEnum() throws Exception {
        String badAction = VALID_EVENT.replace("\"action\":\"DENY\"", "\"action\":\"NOPE\"");
        mockMvc.perform(post("/v1/events/ingest").contentType(MediaType.APPLICATION_JSON).content(badAction))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("MALFORMED_REQUEST"));
    }
}
```

- [ ] **Step 2: Run — expect FAIL.**

- [ ] **Step 3: Implement the response/error records**

`IngestResponse.java`
```java
package com.akamai.wsa.gateway.interfaces.rest;

public record IngestResponse(int acceptedCount) {
}
```

`ApiError.java` / `ApiErrorBody.java`
```java
package com.akamai.wsa.gateway.interfaces.rest.error;

import java.util.List;

public record ApiErrorBody(ApiError error) {
    public record ApiError(String code, String message, List<FieldViolation> details) {
        public record FieldViolation(String field, String message) {
        }
    }
}
```

- [ ] **Step 4: Implement the controller** (single-or-array normalization + manual all-or-nothing validation)

```java
package com.akamai.wsa.gateway.interfaces.rest;

import com.akamai.wsa.contracts.MessageEnvelope;
import com.akamai.wsa.contracts.RawEventMessage;
import com.akamai.wsa.gateway.application.EventRequestMapper;
import com.akamai.wsa.gateway.application.RawEventPublisher;
import com.akamai.wsa.gateway.interfaces.rest.dto.IngestEventRequest;
import com.akamai.wsa.gateway.interfaces.rest.error.BatchValidationException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validator;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;

@RestController
@RequestMapping("/v1/events")
public class IngestController {

    private static final String SCHEMA_VERSION = "1";

    private final ObjectMapper objectMapper;
    private final Validator validator;
    private final EventRequestMapper eventRequestMapper;
    private final RawEventPublisher rawEventPublisher;
    private final Clock clock;

    public IngestController(ObjectMapper objectMapper, Validator validator,
                            EventRequestMapper eventRequestMapper,
                            RawEventPublisher rawEventPublisher, Clock clock) {
        this.objectMapper = objectMapper;
        this.validator = validator;
        this.eventRequestMapper = eventRequestMapper;
        this.rawEventPublisher = rawEventPublisher;
        this.clock = clock;
    }

    @PostMapping("/ingest")
    @ResponseStatus(HttpStatus.CREATED)
    public IngestResponse ingest(@RequestBody JsonNode requestBody,
                                 @RequestHeader(name = "x-correlation-id", required = false) String correlationIdHeader) {
        List<IngestEventRequest> ingestEventRequests = readRequests(requestBody);

        List<BatchValidationException.ItemViolation> violations = new ArrayList<>();
        for (int index = 0; index < ingestEventRequests.size(); index++) {
            Set<ConstraintViolation<IngestEventRequest>> constraintViolations =
                    validator.validate(ingestEventRequests.get(index));
            for (ConstraintViolation<IngestEventRequest> constraintViolation : constraintViolations) {
                violations.add(new BatchValidationException.ItemViolation(
                        "[" + index + "]." + constraintViolation.getPropertyPath(),
                        constraintViolation.getMessage()));
            }
        }
        if (!violations.isEmpty()) {
            throw new BatchValidationException(violations); // all-or-nothing
        }

        String correlationId = resolveCorrelationId(correlationIdHeader);
        Instant occurredAt = Instant.now(clock);
        for (IngestEventRequest ingestEventRequest : ingestEventRequests) {
            RawEventMessage rawEventMessage = eventRequestMapper.toRawEventMessage(ingestEventRequest);
            rawEventPublisher.publish(new MessageEnvelope<>(correlationId, occurredAt, SCHEMA_VERSION, rawEventMessage));
        }
        return new IngestResponse(ingestEventRequests.size());
    }

    private List<IngestEventRequest> readRequests(JsonNode requestBody) {
        try {
            if (requestBody.isArray()) {
                return objectMapper.convertValue(requestBody,
                        objectMapper.getTypeFactory().constructCollectionType(List.class, IngestEventRequest.class));
            }
            return List.of(objectMapper.convertValue(requestBody, IngestEventRequest.class));
        } catch (IllegalArgumentException malformed) {
            throw new MalformedRequestException(malformed.getMessage());
        }
    }

    private String resolveCorrelationId(String correlationIdHeader) {
        return (correlationIdHeader == null || correlationIdHeader.isBlank())
                ? UUID.randomUUID().toString()
                : correlationIdHeader;
    }
}
```

Add the two exceptions and the advice:

`MalformedRequestException.java` (in `interfaces/rest`)
```java
package com.akamai.wsa.gateway.interfaces.rest;

public class MalformedRequestException extends RuntimeException {
    public MalformedRequestException(String message) {
        super(message);
    }
}
```

`BatchValidationException.java` (in `interfaces/rest/error`)
```java
package com.akamai.wsa.gateway.interfaces.rest.error;

import java.util.List;

public class BatchValidationException extends RuntimeException {

    public record ItemViolation(String field, String message) {
    }

    private final transient List<ItemViolation> violations;

    public BatchValidationException(List<ItemViolation> violations) {
        super("batch validation failed");
        this.violations = List.copyOf(violations);
    }

    public List<ItemViolation> violations() {
        return violations;
    }
}
```

`GatewayExceptionHandler.java`
```java
package com.akamai.wsa.gateway.interfaces.rest.error;

import com.akamai.wsa.gateway.interfaces.rest.MalformedRequestException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.List;

@RestControllerAdvice
public class GatewayExceptionHandler {

    @ExceptionHandler(BatchValidationException.class)
    public ResponseEntity<ApiErrorBody> onBatchValidation(BatchValidationException exception) {
        List<ApiErrorBody.ApiError.FieldViolation> details = exception.violations().stream()
                .map(itemViolation -> new ApiErrorBody.ApiError.FieldViolation(
                        itemViolation.field(), itemViolation.message()))
                .toList();
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(new ApiErrorBody(
                new ApiErrorBody.ApiError("VALIDATION_FAILED", "one or more events are invalid", details)));
    }

    @ExceptionHandler({MalformedRequestException.class, HttpMessageNotReadableException.class})
    public ResponseEntity<ApiErrorBody> onMalformed(Exception exception) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(new ApiErrorBody(
                new ApiErrorBody.ApiError("MALFORMED_REQUEST", "request body could not be parsed", List.of())));
    }
}
```

Provide a `Clock` bean (`@Bean Clock clock() { return Clock.systemUTC(); }`) in an infrastructure config if the restructure phase didn't already.

- [ ] **Step 5: Run — expect PASS** (`mvn -q -pl services/gateway test -Dtest=IngestControllerTest`).
- [ ] **Step 6: Run the whole module** — `mvn -q -pl services/gateway test`.
- [ ] **Step 7: Commit** — `feat(gateway): add all-or-nothing ingest endpoint publishing to events.raw`.

---

### Task 4: Dry-run and milestone

- [ ] **Step 1:** `mvn -q -pl services/gateway -am spring-boot:run` (needs a broker; run `docker compose up -d kafka` first, or point at the compose kafka).
- [ ] **Step 2:** curl the endpoint:
```bash
curl -s -XPOST localhost:8081/v1/events/ingest -H 'content-type: application/json' \
  -H 'x-correlation-id: demo-1' -d @sample-event.json    # => {"acceptedCount":1}
```
Verify a message on `events.raw` (via `kafka-console-consumer` or the enrichment service once it exists).
- [ ] **Step 3: Tag** — `git tag v0.2-gateway`.

---

## Self-Review

**Spec coverage:** single + batch ingest ✓; required-field/enum/timestamp validation ✓; all-or-nothing 400 with details ✓ (Task 3); 201 with acceptedCount ✓; publish enveloped, correlation-tagged, clientIp-keyed to `events.raw` ✓ (Task 2); no storage in gateway ✓.

**Placeholder scan:** none — all steps carry complete code. The one implementer note (test probe `@TestConfiguration`) is explicit about what to build.

**Type consistency:** `RawEventMessage`/`RuleMessage`/`GeoLocationMessage`/`MessageEnvelope` and enums come from `contracts`; `EventRequestMapper.toRawEventMessage`, `RawEventPublisher.publish(MessageEnvelope<RawEventMessage>)`, and `IngestResponse(acceptedCount)` are used identically across tasks. `receivedAt` is intentionally NOT set here — enrichment stamps it (SDD §5.1).

**Cross-service note:** depends on `contracts` field shapes (Task 1 of the restructure plan) and the gateway module scaffold; the `KafkaTopics` prefix `wsa.kafka.topics` matches `application.yml`.
