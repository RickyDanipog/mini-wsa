package com.akamai.wsa.gateway.application;

import com.akamai.wsa.gateway.interfaces.rest.MalformedRequestException;
import com.akamai.wsa.gateway.interfaces.rest.dto.IngestEventRequest;
import com.fasterxml.jackson.databind.JsonMappingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.exc.InvalidFormatException;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.time.Instant;
import java.util.Arrays;
import java.util.List;

public class EventRequestReader {

    private final ObjectMapper objectMapper;

    public EventRequestReader(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public List<IngestEventRequest> read(JsonNode requestBody) {
        if (requestBody == null || requestBody.isNull()) {
            throw new MalformedRequestException("body", "Expected an event object or array");
        }
        try {
            if (requestBody.isArray()) {
                for (int index = 0; index < requestBody.size(); index++) {
                    if (requestBody.get(index).isNull()) {
                        throw new MalformedRequestException("[%d]".formatted(index), "Expected an event object");
                    }
                }
                return objectMapper.convertValue(requestBody, objectMapper.getTypeFactory()
                        .constructCollectionType(List.class, IngestEventRequest.class));
            }
            return List.of(objectMapper.convertValue(requestBody, IngestEventRequest.class));
        } catch (IllegalArgumentException malformed) {
            if (malformed.getCause() instanceof JsonMappingException mapping) {
                throw new MalformedRequestException(fieldPath(mapping), expectedFormat(mapping));
            }
            throw new MalformedRequestException("body", "Expected an event object or array");
        }
    }

    private static String fieldPath(JsonMappingException mapping) {
        StringBuilder path = new StringBuilder();
        for (JsonMappingException.Reference part : mapping.getPath()) {
            if (part.getFieldName() != null) {
                if (!path.isEmpty()) {
                    path.append('.');
                }
                path.append(part.getFieldName());
            } else if (part.getIndex() >= 0) {
                path.append('[').append(part.getIndex()).append(']');
            }
        }
        return path.isEmpty() ? "body" : path.toString();
    }

    private static String expectedFormat(JsonMappingException mapping) {
        if (mapping instanceof InvalidFormatException invalid) {
            Class<?> type = invalid.getTargetType();
            if (type.isEnum()) {
                return "Expected one of %s".formatted(Arrays.toString(type.getEnumConstants()));
            }
            if (type == Instant.class) {
                return "Expected an ISO-8601 timestamp";
            }
        }
        return "Unexpected value or JSON type";
    }
}
