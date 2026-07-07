package com.akamai.wsa.generator.generate;

import com.akamai.wsa.generator.GeneratorProperties;
import com.akamai.wsa.generator.model.GeneratedEvent;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

class DatasetGeneratorTest {

    private GeneratorProperties propertiesWith(int totalEvents, int waveCount, int waveSize) {
        return new GeneratorProperties(
                123L, totalEvents, waveCount, waveSize,
                Instant.parse("2026-05-20T14:00:00Z"),
                "http://localhost:8081", 500,
                GeneratorProperties.OutputMode.STDOUT, "out.json");
    }

    @Test
    void producesExactlyTheRequestedTotal() {
        DatasetGenerator datasetGenerator = new DatasetGenerator(propertiesWith(1000, 5, 40));

        assertThat(datasetGenerator.generate()).hasSize(1000);
    }

    @Test
    void sameSeedProducesIdenticalDataset() {
        assertThat(new DatasetGenerator(propertiesWith(500, 3, 30).withSeed(55L)).generate())
                .isEqualTo(new DatasetGenerator(propertiesWith(500, 3, 30).withSeed(55L)).generate());
    }

    @Test
    void containsAtLeastOneRepeatOffenderBurst() {
        DatasetGenerator datasetGenerator = new DatasetGenerator(propertiesWith(600, 4, 30));

        List<GeneratedEvent> events = datasetGenerator.generate();

        boolean hasBurst = events.stream()
                .collect(Collectors.groupingBy(event -> event.clientIp() + "|" + event.path()))
                .values().stream()
                .anyMatch(group -> group.size() > 5 && withinTenMinutes(group));

        assertThat(hasBurst).isTrue();
    }

    private boolean withinTenMinutes(List<GeneratedEvent> group) {
        Instant earliest = group.stream().map(GeneratedEvent::timestamp).min(Instant::compareTo).orElseThrow();
        Instant latest = group.stream().map(GeneratedEvent::timestamp).max(Instant::compareTo).orElseThrow();
        return Duration.between(earliest, latest).compareTo(Duration.ofMinutes(10)) <= 0;
    }
}
