package com.akamai.wsa.gateway.application;

import com.akamai.wsa.contracts.MessageEnvelope;
import com.akamai.wsa.contracts.RawEventMessage;
import com.akamai.wsa.gateway.infrastructure.messaging.RawEventPublisher;
import com.akamai.wsa.gateway.interfaces.rest.dto.IngestEventRequest;
import com.akamai.wsa.gateway.interfaces.rest.error.BatchValidationException;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validator;

import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

public class IngestEventsService implements IngestEvents {

    private final Validator validator;
    private final EventRequestMapper eventRequestMapper;
    private final RawEventPublisher rawEventPublisher;
    private final Clock clock;

    public IngestEventsService(Validator validator, EventRequestMapper eventRequestMapper,
                               RawEventPublisher rawEventPublisher, Clock clock) {
        this.validator = validator;
        this.eventRequestMapper = eventRequestMapper;
        this.rawEventPublisher = rawEventPublisher;
        this.clock = clock;
    }

    @Override
    public int ingest(List<IngestEventRequest> ingestEventRequests, String correlationId) {
        validateAllOrNothing(ingestEventRequests);

        Instant occurredAt = Instant.now(clock);
        for (IngestEventRequest ingestEventRequest : ingestEventRequests) {
            RawEventMessage rawEventMessage = eventRequestMapper.toRawEventMessage(ingestEventRequest);
            rawEventPublisher.publish(MessageEnvelope.of(correlationId, occurredAt, rawEventMessage));
        }
        return ingestEventRequests.size();
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
}
