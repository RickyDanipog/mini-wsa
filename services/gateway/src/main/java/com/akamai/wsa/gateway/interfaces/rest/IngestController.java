package com.akamai.wsa.gateway.interfaces.rest;

import com.akamai.wsa.contracts.MessageEnvelope;
import com.akamai.wsa.contracts.RawEventMessage;
import com.akamai.wsa.gateway.application.EventRequestMapper;
import com.akamai.wsa.gateway.infrastructure.messaging.RawEventPublisher;
import com.akamai.wsa.gateway.interfaces.rest.dto.IngestEventRequest;
import com.akamai.wsa.gateway.interfaces.rest.error.BatchValidationException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
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
class IngestController {

    private static final Logger logger = LoggerFactory.getLogger(IngestController.class);
    private static final String CORRELATION_ID_HEADER = "x-correlation-id";

    private final ObjectMapper objectMapper;
    private final Validator validator;
    private final EventRequestMapper eventRequestMapper;
    private final RawEventPublisher rawEventPublisher;
    private final Clock clock;

    IngestController(ObjectMapper objectMapper, Validator validator,
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
    IngestResponse ingest(
            @RequestBody JsonNode requestBody,
            @RequestHeader(name = CORRELATION_ID_HEADER, required = false) String correlationIdHeader) {

        List<IngestEventRequest> ingestEventRequests = readRequests(requestBody);
        validateAllOrNothing(ingestEventRequests);

        String correlationId = resolveCorrelationId(correlationIdHeader);
        Instant occurredAt = Instant.now(clock);
        for (IngestEventRequest ingestEventRequest : ingestEventRequests) {
            RawEventMessage rawEventMessage = eventRequestMapper.toRawEventMessage(ingestEventRequest);
            rawEventPublisher.publish(MessageEnvelope.of(correlationId, occurredAt, rawEventMessage));
        }

        logger.info("IngestController - ingest accepted={} correlationId={}",
                ingestEventRequests.size(), correlationId);
        return new IngestResponse(ingestEventRequests.size());
    }

    private List<IngestEventRequest> readRequests(JsonNode requestBody) {
        try {
            if (requestBody.isArray()) {
                return objectMapper.convertValue(requestBody, objectMapper.getTypeFactory()
                        .constructCollectionType(List.class, IngestEventRequest.class));
            }
            return List.of(objectMapper.convertValue(requestBody, IngestEventRequest.class));
        } catch (IllegalArgumentException malformed) {
            throw new MalformedRequestException(malformed.getMessage());
        }
    }

    private void validateAllOrNothing(List<IngestEventRequest> ingestEventRequests) {
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
            throw new BatchValidationException(violations);
        }
    }

    private String resolveCorrelationId(String correlationIdHeader) {
        return (correlationIdHeader == null || correlationIdHeader.isBlank())
                ? UUID.randomUUID().toString()
                : correlationIdHeader;
    }
}
