package com.akamai.wsa.gateway.interfaces.rest;

import com.akamai.wsa.gateway.application.EventIngestionService;
import com.akamai.wsa.gateway.application.EventRequestReader;
import com.akamai.wsa.gateway.interfaces.rest.dto.IngestEventRequest;
import com.fasterxml.jackson.databind.JsonNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/v1/events")
class IngestController {

    private static final Logger logger = LoggerFactory.getLogger(IngestController.class);
    private static final String CORRELATION_ID_HEADER = "x-correlation-id";

    private final EventRequestReader eventRequestReader;
    private final EventIngestionService eventIngestionService;

    IngestController(EventRequestReader eventRequestReader, EventIngestionService eventIngestionService) {
        this.eventRequestReader = eventRequestReader;
        this.eventIngestionService = eventIngestionService;
    }

    @PostMapping("/ingest")
    @ResponseStatus(HttpStatus.CREATED)
    IngestResponse ingest(
            @RequestBody JsonNode requestBody,
            @RequestHeader(name = CORRELATION_ID_HEADER, required = false) String correlationIdHeader) {

        List<IngestEventRequest> ingestEventRequests = eventRequestReader.read(requestBody);
        String correlationId = resolveCorrelationId(correlationIdHeader);
        int accepted = eventIngestionService.ingest(ingestEventRequests, correlationId);

        logger.info("IngestController - ingest accepted={} correlationId={}", accepted, correlationId);
        return new IngestResponse(accepted);
    }

    private String resolveCorrelationId(String correlationIdHeader) {
        return (correlationIdHeader == null || correlationIdHeader.isBlank())
                ? UUID.randomUUID().toString()
                : correlationIdHeader;
    }
}
