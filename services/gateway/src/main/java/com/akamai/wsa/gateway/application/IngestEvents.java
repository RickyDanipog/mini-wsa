package com.akamai.wsa.gateway.application;

import com.akamai.wsa.gateway.interfaces.rest.dto.IngestEventRequest;

import java.util.List;

public interface IngestEvents {

    int ingest(List<IngestEventRequest> ingestEventRequests, String correlationId);
}
