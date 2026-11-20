package com.akamai.wsa.enrichment.infrastructure;

import com.akamai.wsa.enrichment.domain.port.OffenderWindow;
import com.akamai.wsa.enrichment.domain.port.ProcessedEventLog;
import com.akamai.wsa.enrichment.infrastructure.dedup.RedisProcessedEventLog;
import com.akamai.wsa.enrichment.infrastructure.window.RedisOffenderWindow;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import java.time.Duration;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

@Testcontainers
@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.NONE,
        properties = "spring.kafka.listener.auto-startup=false")
class RedisAdaptersIntegrationTest {

    private static final Duration TEN_MINUTES = Duration.ofMinutes(10);
    private static final Instant NOW = Instant.parse("2026-05-20T14:00:00Z");

    @Container
    static final GenericContainer<?> REDIS =
            new GenericContainer<>(DockerImageName.parse("redis:7")).withExposedPorts(6379);

    @DynamicPropertySource
    static void redisProperties(DynamicPropertyRegistry registry) {
        registry.add("wsa.storage", () -> "redis");
        registry.add("spring.data.redis.host", REDIS::getHost);
        registry.add("spring.data.redis.port", REDIS::getFirstMappedPort);
    }

    @Autowired
    OffenderWindow offenderWindow;

    @Autowired
    ProcessedEventLog processedEventLog;

    @Test
    void redisAdaptersAreActiveUnderRedisStorage() {
        assertThat(offenderWindow).isInstanceOf(RedisOffenderWindow.class);
        assertThat(processedEventLog).isInstanceOf(RedisProcessedEventLog.class);
    }

    @Test
    void sixthEventInWindowIsFirstToCrossRepeatOffenderThreshold() {
        String clientIp = "10.0.0.1";
        offenderWindow.recordEvent(clientIp, "evt-56", NOW.minus(Duration.ofMinutes(9)));
        offenderWindow.recordEvent(clientIp, "evt-57", NOW.minus(Duration.ofMinutes(7)));
        offenderWindow.recordEvent(clientIp, "evt-58", NOW.minus(Duration.ofMinutes(5)));
        offenderWindow.recordEvent(clientIp, "evt-59", NOW.minus(Duration.ofMinutes(3)));
        offenderWindow.recordEvent(clientIp, "evt-60", NOW.minus(Duration.ofMinutes(1)));
        assertThat(offenderWindow.countRecentEventsFromClient(clientIp, TEN_MINUTES, NOW)).isEqualTo(5);

        offenderWindow.recordEvent(clientIp, "evt-63", NOW);
        assertThat(offenderWindow.countRecentEventsFromClient(clientIp, TEN_MINUTES, NOW)).isEqualTo(6);
    }

    @Test
    void countsDistinctEventsAtTheSameMillisecond() {
        String clientIp = "10.0.0.3";
        for (int i = 1; i <= 6; i++) {
            offenderWindow.recordEvent(clientIp, "evt-" + i, NOW);
        }
        offenderWindow.recordEvent(clientIp, "evt-6", NOW);
        assertThat(offenderWindow.countRecentEventsFromClient(clientIp, TEN_MINUTES, NOW)).isEqualTo(6);
    }

    @Test
    void includesEntryAtBoundaryAndExcludesEntryJustOutsideWindow() {
        String clientIp = "10.0.0.2";
        offenderWindow.recordEvent(clientIp, "evt-70", NOW.minus(TEN_MINUTES));
        offenderWindow.recordEvent(clientIp, "evt-71", NOW.minus(TEN_MINUTES).minusMillis(1));
        assertThat(offenderWindow.countRecentEventsFromClient(clientIp, TEN_MINUTES, NOW)).isEqualTo(1);
    }

    @Test
    void markProcessedReturnsTrueOnFirstSightAndFalseAfterwards() {
        assertThat(processedEventLog.markProcessed("evt-redis-1")).isTrue();
        assertThat(processedEventLog.markProcessed("evt-redis-1")).isFalse();
        assertThat(processedEventLog.markProcessed("evt-redis-2")).isTrue();
    }
}
