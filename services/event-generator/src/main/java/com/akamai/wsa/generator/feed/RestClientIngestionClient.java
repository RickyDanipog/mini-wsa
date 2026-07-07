package com.akamai.wsa.generator.feed;

import com.akamai.wsa.generator.GeneratorProperties;
import com.akamai.wsa.generator.model.GeneratedEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

import java.util.List;

/**
 * POSTs batches to the gateway's ingest endpoint ({@code targetUrl} +
 * {@code /v1/events/ingest}), parses the {@code 201 {"acceptedCount": N}} body,
 * and catches a {@code 400} (the gateway rejects a batch all-or-nothing) by
 * logging and returning 0 so the run continues.
 */
@Component
public class RestClientIngestionClient implements IngestionClient {

    private static final Logger logger = LoggerFactory.getLogger(RestClientIngestionClient.class);
    private static final String INGEST_PATH = "/v1/events/ingest";

    private final RestClient restClient;
    private final String ingestUrl;

    public RestClientIngestionClient(RestClient.Builder restClientBuilder, GeneratorProperties properties) {
        this.restClient = restClientBuilder.build();
        this.ingestUrl = stripTrailingSlash(properties.targetUrl()) + INGEST_PATH;
    }

    public record IngestAcceptedResponse(int acceptedCount) {
    }

    @Override
    public int postBatch(List<GeneratedEvent> batch) {
        try {
            IngestAcceptedResponse response = restClient.post()
                    .uri(ingestUrl)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(batch)
                    .retrieve()
                    .body(IngestAcceptedResponse.class);
            return response == null ? 0 : response.acceptedCount();
        } catch (RestClientResponseException rejection) {
            // Gateway is all-or-nothing per batch: one invalid event -> 400 for the whole batch.
            // Log and continue rather than aborting the run.
            logger.warn("RestClientIngestionClient - batch rejected (size={}, status={})",
                    batch.size(), rejection.getStatusCode());
            return 0;
        }
    }

    private static String stripTrailingSlash(String url) {
        return url.endsWith("/") ? url.substring(0, url.length() - 1) : url;
    }
}
