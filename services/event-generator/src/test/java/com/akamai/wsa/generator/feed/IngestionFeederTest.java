package com.akamai.wsa.generator.feed;

import com.akamai.wsa.generator.model.GeneratedEvent;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class IngestionFeederTest {

    @Test
    void postsInBatchesAndSumsAcceptedCounts() {
        RecordingIngestionClient recordingClient = new RecordingIngestionClient();
        IngestionFeeder feeder = new IngestionFeeder(recordingClient);

        int accepted = feeder.feed(eventList(250), 100);

        assertThat(recordingClient.batchSizes).containsExactly(100, 100, 50);
        assertThat(accepted).isEqualTo(250);
    }

    @Test
    void continuesWhenABatchIsRejected() {
        // a client that rejects (returns 0) must not abort the run; remaining batches still post
        IngestionClient rejectingThenAccepting = new IngestionClient() {
            private int call = 0;

            @Override
            public int postBatch(List<GeneratedEvent> batch) {
                return (call++ == 0) ? 0 : batch.size();
            }
        };
        IngestionFeeder feeder = new IngestionFeeder(rejectingThenAccepting);

        int accepted = feeder.feed(eventList(250), 100);

        assertThat(accepted).isEqualTo(150); // first batch rejected (0), next two accepted (100+50)
    }

    private List<GeneratedEvent> eventList(int count) {
        List<GeneratedEvent> events = new ArrayList<>();
        for (int sequenceNumber = 0; sequenceNumber < count; sequenceNumber++) {
            events.add(new GeneratedEvent("evt-" + sequenceNumber, Instant.parse("2026-05-20T14:00:00Z"),
                    14227, "pol", "1.1.1.1", "h", "/p", "GET", 200, "ua", null, "DENY", null, 1, 1));
        }
        return events;
    }

    private static final class RecordingIngestionClient implements IngestionClient {
        private final List<Integer> batchSizes = new ArrayList<>();

        @Override
        public int postBatch(List<GeneratedEvent> batch) {
            batchSizes.add(batch.size());
            return batch.size();
        }
    }
}
