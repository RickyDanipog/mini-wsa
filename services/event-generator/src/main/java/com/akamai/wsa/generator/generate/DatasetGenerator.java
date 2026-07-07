package com.akamai.wsa.generator.generate;

import com.akamai.wsa.generator.GeneratorProperties;
import com.akamai.wsa.generator.model.GeneratedEvent;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * Assembles a full dataset: background events plus {@code waveCount} attack
 * waves, each a burst of {@code waveSize} events from one client IP hitting one
 * path within a two-minute span (a repeat-offender burst of &gt;5-in-10-min that
 * exercises the enrichment repeat-offender rule). Deterministic for a given seed.
 */
@Component
public class DatasetGenerator {

    private static final List<String> WAVE_PATHS = List.of("/login", "/admin", "/api/v1/login", "/checkout");
    private static final int WAVE_SPAN_SECONDS = 120;

    private final GeneratorProperties properties;
    private final SecurityEventGenerator eventGenerator;

    public DatasetGenerator(GeneratorProperties properties) {
        this.properties = properties;
        this.eventGenerator = new SecurityEventGenerator(properties.baseTimestamp());
    }

    public List<GeneratedEvent> generate() {
        Random random = new Random(properties.seed());
        int waveEventTotal = Math.min(properties.waveCount() * properties.waveSize(), properties.totalEvents());
        int backgroundTotal = properties.totalEvents() - waveEventTotal;

        List<GeneratedEvent> events = new ArrayList<>(properties.totalEvents());
        int sequenceNumber = 0;

        for (int backgroundIndex = 0; backgroundIndex < backgroundTotal; backgroundIndex++) {
            events.add(eventGenerator.generateNormalEvent(sequenceNumber++, random));
        }

        int remainingWaveEvents = waveEventTotal;
        for (int waveIndex = 0; waveIndex < properties.waveCount() && remainingWaveEvents > 0; waveIndex++) {
            String attackerIp = eventGenerator.randomIp(random);
            String targetPath = WAVE_PATHS.get(random.nextInt(WAVE_PATHS.size()));
            Instant waveStart = properties.baseTimestamp().plus(waveIndex, ChronoUnit.HOURS);
            int eventsInThisWave = Math.min(properties.waveSize(), remainingWaveEvents);
            for (int burstIndex = 0; burstIndex < eventsInThisWave; burstIndex++) {
                Instant timestamp = waveStart.plusSeconds(random.nextInt(WAVE_SPAN_SECONDS));
                events.add(eventGenerator.generateWaveEvent(
                        sequenceNumber++, attackerIp, targetPath, timestamp, random));
            }
            remainingWaveEvents -= eventsInThisWave;
        }

        return List.copyOf(events);
    }
}
