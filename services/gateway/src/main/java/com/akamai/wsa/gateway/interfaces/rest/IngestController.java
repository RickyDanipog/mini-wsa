package com.akamai.wsa.gateway.interfaces.rest;

import com.akamai.wsa.contracts.MessageEnvelope;
import com.akamai.wsa.contracts.RawEventMessage;
import com.akamai.wsa.gateway.infrastructure.messaging.RawEventPublisher;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Write-path entry point. Accepts a single event or an array, wraps each in an
 * envelope, and publishes to {@code events.raw}. (Thin skeleton: full Bean
 * Validation + structured 400 details arrive with the gateway service plan.)
 */
@RestController
@RequestMapping("/v1/events")
class IngestController {

    private static final String CORRELATION_ID_HEADER = "x-correlation-id";

    private final RawEventPublisher rawEventPublisher;
    private final ObjectMapper objectMapper;
    private final Clock clock;

    IngestController(RawEventPublisher rawEventPublisher, ObjectMapper objectMapper, Clock clock) {
        this.rawEventPublisher = rawEventPublisher;
        this.objectMapper = objectMapper;
        this.clock = clock;
    }

    @PostMapping("/ingest")
    ResponseEntity<Map<String, Object>> ingest(
            @RequestBody JsonNode requestBody,
            @RequestHeader(value = CORRELATION_ID_HEADER, required = false) String correlationIdHeader) throws Exception {

        List<JsonNode> eventNodes = new ArrayList<>();
        if (requestBody.isArray()) {
            requestBody.forEach(eventNodes::add);
        } else {
            eventNodes.add(requestBody);
        }

        String correlationId = (correlationIdHeader == null || correlationIdHeader.isBlank())
                ? UUID.randomUUID().toString()
                : correlationIdHeader;
        Instant occurredAt = Instant.now(clock);

        for (JsonNode eventNode : eventNodes) {
            RawEventMessage rawEvent = objectMapper.treeToValue(eventNode, RawEventMessage.class);
            rawEventPublisher.publish(MessageEnvelope.of(correlationId, occurredAt, rawEvent));
        }

        return ResponseEntity.status(HttpStatus.CREATED)
                .body(Map.of("acceptedCount", eventNodes.size()));
    }
}
