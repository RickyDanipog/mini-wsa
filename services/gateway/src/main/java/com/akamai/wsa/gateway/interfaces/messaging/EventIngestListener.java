package com.akamai.wsa.gateway.interfaces.messaging;

import com.akamai.wsa.gateway.application.EventIngestionService;
import com.akamai.wsa.gateway.application.EventRequestReader;
import com.akamai.wsa.gateway.interfaces.rest.MalformedRequestException;
import com.akamai.wsa.gateway.interfaces.rest.dto.IngestEventRequest;
import com.akamai.wsa.gateway.interfaces.rest.error.BatchValidationException;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.common.header.Header;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.UUID;

@Component
public class EventIngestListener {

    private static final Logger logger = LoggerFactory.getLogger(EventIngestListener.class);
    private static final String CORRELATION_ID_HEADER = "x-correlation-id";

    private final ObjectMapper objectMapper;
    private final EventRequestReader eventRequestReader;
    private final EventIngestionService eventIngestionService;

    public EventIngestListener(ObjectMapper objectMapper, EventRequestReader eventRequestReader,
                               EventIngestionService eventIngestionService) {
        this.objectMapper = objectMapper;
        this.eventRequestReader = eventRequestReader;
        this.eventIngestionService = eventIngestionService;
    }

    @KafkaListener(topics = "${wsa.topics.events-ingest}", groupId = "gateway-ingest")
    public void onEventIngestMessage(ConsumerRecord<String, String> record) {
        String correlationId = resolveCorrelationId(record);
        try {
            List<IngestEventRequest> ingestEventRequests = eventRequestReader.read(objectMapper.readTree(record.value()));
            int accepted = eventIngestionService.ingest(ingestEventRequests, correlationId);
            logger.info("EventIngestListener - ingested accepted={} correlationId={}", accepted, correlationId);
        } catch (MalformedRequestException | BatchValidationException | JsonProcessingException rejected) {
            logger.warn("EventIngestListener - rejected message reason={} correlationId={}",
                    rejected.getMessage(), correlationId);
        }
    }

    private String resolveCorrelationId(ConsumerRecord<String, String> record) {
        Header header = record.headers().lastHeader(CORRELATION_ID_HEADER);
        if (header == null || header.value() == null || header.value().length == 0) {
            return UUID.randomUUID().toString();
        }
        return new String(header.value(), StandardCharsets.UTF_8);
    }
}
