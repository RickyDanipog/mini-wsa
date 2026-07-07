package com.akamai.wsa.generator.feed;

import com.akamai.wsa.generator.model.GeneratedEvent;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Splits a dataset into batches and POSTs each via an {@link IngestionClient},
 * summing accepted counts. A rejected batch (accepted 0) does not abort the run.
 */
@Component
public class IngestionFeeder {

    private final IngestionClient ingestionClient;

    public IngestionFeeder(IngestionClient ingestionClient) {
        this.ingestionClient = ingestionClient;
    }

    public int feed(List<GeneratedEvent> events, int batchSize) {
        int totalAccepted = 0;
        for (int start = 0; start < events.size(); start += batchSize) {
            int end = Math.min(start + batchSize, events.size());
            totalAccepted += ingestionClient.postBatch(events.subList(start, end));
        }
        return totalAccepted;
    }
}
