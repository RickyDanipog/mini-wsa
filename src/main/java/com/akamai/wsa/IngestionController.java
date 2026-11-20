package com.akamai.wsa;

import com.fasterxml.jackson.databind.JsonMappingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.validation.Validator;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
class IngestionController {
    private final EventIngestion ingestion;
    private final ObjectMapper mapper;
    private final Validator validator;

    IngestionController(EventIngestion ingestion, ObjectMapper mapper, Validator validator) {
        this.ingestion = ingestion;
        this.mapper = mapper;
        this.validator = validator;
    }

    @PostMapping("/v1/events/ingest")
    @ResponseStatus(HttpStatus.CREATED)
    Accepted ingest(@RequestBody JsonNode body) {
        List<SecurityEvent> events = new ArrayList<>();
        List<Detail> errors = new ArrayList<>();
        if (body.isArray()) {
            for (int index = 0; index < body.size(); index++) {
                read(body.get(index), "[" + index + "].", events, errors);
            }
            if (body.isEmpty()) {
                errors.add(new Detail("body", "Expected at least one event"));
            }
        } else {
            read(body, "", events, errors);
        }
        if (!errors.isEmpty()) {
            throw new InvalidEvents(errors);
        }
        ingestion.store(events);
        return new Accepted(events.size());
    }

    private void read(JsonNode node, String prefix, List<SecurityEvent> events, List<Detail> errors) {
        if (!node.isObject()) {
            String field = prefix.isEmpty() ? "body" : prefix.substring(0, prefix.length() - 1);
            errors.add(new Detail(field, "Expected an event object"));
            return;
        }
        if (node.hasNonNull("timestamp") && !node.get("timestamp").isTextual()) {
            errors.add(new Detail(prefix + "timestamp", "Expected an ISO-8601 timestamp"));
            return;
        }
        try {
            SecurityEvent event = mapper.treeToValue(node, SecurityEvent.class);
            validator.validate(event).stream()
                    .map(violation -> new Detail(prefix + violation.getPropertyPath(), violation.getMessage()))
                    .sorted(Comparator.comparing(Detail::field))
                    .forEach(errors::add);
            events.add(event);
        } catch (JsonMappingException exception) {
            String field = exception.getPath().stream()
                    .map(JsonMappingException.Reference::getFieldName)
                    .filter(name -> name != null)
                    .reduce((parent, child) -> parent + "." + child)
                    .orElse("body");
            errors.add(new Detail(prefix + field, "Invalid field value"));
        } catch (com.fasterxml.jackson.core.JsonProcessingException exception) {
            errors.add(new Detail(prefix + "body", "Invalid event"));
        }
    }

    @ExceptionHandler(InvalidEvents.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    ErrorResponse invalid(InvalidEvents exception) {
        return new ErrorResponse(new ErrorBody("VALIDATION_FAILED", "one or more events are invalid", exception.details));
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    ErrorResponse malformed() {
        return new ErrorResponse(new ErrorBody("MALFORMED_REQUEST", "request body could not be parsed",
                List.of(new Detail("body", "Expected a JSON request body"))));
    }

    record Accepted(int acceptedCount) {}
    record ErrorResponse(ErrorBody error) {}
    record ErrorBody(String code, String message, List<Detail> details) {}
    record Detail(String field, String message) {}

    private static final class InvalidEvents extends RuntimeException {
        private final List<Detail> details;

        private InvalidEvents(List<Detail> details) {
            this.details = List.copyOf(details);
        }
    }
}
