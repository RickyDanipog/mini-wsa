package com.akamai.wsa.generator.generate;

import com.akamai.wsa.generator.model.GeneratedEvent;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.Random;
import java.util.Set;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;

class SecurityEventGeneratorTest {

    private static final Set<String> VALID_CATEGORIES =
            Set.of("INJECTION", "XSS", "PROTOCOL_VIOLATION", "DATA_LEAKAGE", "BOT", "DOS", "RATE_LIMIT");
    private static final Set<String> VALID_ACTIONS = Set.of("DENY", "ALERT", "MONITOR");
    private static final Set<String> VALID_SEVERITIES = Set.of("CRITICAL", "HIGH", "MEDIUM", "LOW");

    private final SecurityEventGenerator generator =
            new SecurityEventGenerator(Instant.parse("2026-05-20T14:00:00Z"));

    @Test
    void sameSeedProducesIdenticalEvents() {
        List<GeneratedEvent> first = generateTen(new Random(99L));
        List<GeneratedEvent> second = generateTen(new Random(99L));

        assertThat(first).isEqualTo(second);
    }

    @Test
    void producesSchemaValidEvents() {
        Random random = new Random(7L);

        for (int sequenceNumber = 0; sequenceNumber < 200; sequenceNumber++) {
            GeneratedEvent event = generator.generateNormalEvent(sequenceNumber, random);

            assertThat(event.eventId()).isNotBlank();
            assertThat(event.timestamp()).isNotNull();
            assertThat(event.clientIp()).matches("\\d{1,3}\\.\\d{1,3}\\.\\d{1,3}\\.\\d{1,3}");
            assertThat(event.path()).startsWith("/");
            assertThat(event.method()).isNotBlank();
            assertThat(event.hostname()).isNotBlank();
            assertThat(event.rule()).isNotNull();
            assertThat(VALID_CATEGORIES).contains(event.rule().category());
            assertThat(VALID_SEVERITIES).contains(event.rule().severity());
            assertThat(VALID_ACTIONS).contains(event.action());
            assertThat(event.geoLocation()).isNotNull();
            assertThat(event.requestSize()).isPositive();
            assertThat(event.responseSize()).isPositive();
        }
    }

    @Test
    void coversLoginAndAdminPaths() {
        Random random = new Random(3L);

        List<String> paths = IntStream.range(0, 500)
                .mapToObj(sequenceNumber -> generator.generateNormalEvent(sequenceNumber, random).path())
                .toList();

        assertThat(paths).contains("/login");
        assertThat(paths).contains("/admin");
    }

    private List<GeneratedEvent> generateTen(Random random) {
        return IntStream.range(0, 10)
                .mapToObj(sequenceNumber -> generator.generateNormalEvent(sequenceNumber, random))
                .toList();
    }
}
