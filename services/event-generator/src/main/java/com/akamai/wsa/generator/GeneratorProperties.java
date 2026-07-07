package com.akamai.wsa.generator;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

import java.time.Instant;

@ConfigurationProperties(prefix = "wsa.generator")
public record GeneratorProperties(
        @DefaultValue("1") long seed,
        @DefaultValue("10000") int totalEvents,
        @DefaultValue("20") int waveCount,
        @DefaultValue("50") int waveSize,
        @DefaultValue("2026-05-20T14:00:00Z") Instant baseTimestamp,
        @DefaultValue("http://localhost:8081") String targetUrl,
        @DefaultValue("500") int batchSize,
        @DefaultValue("STDOUT") OutputMode outputMode,
        @DefaultValue("generated-events.json") String outputFile
) {
    public enum OutputMode {
        JSON_FILE,
        STDOUT,
        HTTP
    }

    public GeneratorProperties withSeed(long newSeed) {
        return new GeneratorProperties(newSeed, totalEvents, waveCount, waveSize, baseTimestamp,
                targetUrl, batchSize, outputMode, outputFile);
    }
}
