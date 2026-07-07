package com.akamai.wsa.generator.feed;

import com.akamai.wsa.generator.model.GeneratedEvent;

import java.util.List;

public interface IngestionClient {

    int postBatch(List<GeneratedEvent> batch);
}
