package com.akamai.wsa.application.ingest;

import com.akamai.wsa.domain.model.DataLogRecord;

import java.util.List;

/**
 * Inbound use case: enrich and store a batch of validated raw events.
 * Enrichment (classify + score + stamp receivedAt) happens inside the
 * implementation; the caller passes already-validated {@link DataLogRecord}s.
 */
public interface IngestSecurityEvents {

    IngestionOutcome ingest(List<DataLogRecord> dataLogRecords);
}
