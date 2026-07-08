package com.akamai.wsa.gateway.application;

import com.akamai.wsa.gateway.interfaces.rest.MalformedRequestException;
import com.akamai.wsa.gateway.interfaces.rest.dto.IngestEventRequest;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.util.List;

public class EventRequestReader {

    private final ObjectMapper objectMapper;

    public EventRequestReader(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public List<IngestEventRequest> read(JsonNode requestBody) {
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
}
