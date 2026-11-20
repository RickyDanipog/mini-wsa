package com.akamai.wsa;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.time.Clock;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.List;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
class EventIngestion {
    private static final Duration OFFENDER_WINDOW = Duration.ofMinutes(10);
    private static final int OFFENDER_THRESHOLD = 5;
    private static final int LOCK_STRIPES = 1024;

    private final JdbcTemplate jdbc;
    private final ObjectMapper mapper;
    private final Clock clock;

    EventIngestion(JdbcTemplate jdbc, ObjectMapper mapper, Clock clock) {
        this.jdbc = jdbc;
        this.mapper = mapper;
        this.clock = clock;
    }

    @Transactional
    public void store(List<SecurityEvent> events) {
        events.stream().map(event -> stripe(event.eventId())).distinct().sorted().forEach(this::lockEvent);
        events.stream().map(event -> stripe(event.clientIp())).distinct().sorted().forEach(this::lockClient);
        OffsetDateTime receivedAt = clock.instant().truncatedTo(ChronoUnit.MICROS).atOffset(ZoneOffset.UTC);
        for (SecurityEvent event : events) {
            store(event, receivedAt);
        }
    }

    private static int stripe(String key) {
        return Math.floorMod(key.hashCode(), LOCK_STRIPES);
    }

    private void lockEvent(int key) {
        jdbc.query("SELECT pg_advisory_xact_lock(1, ?)", result -> {}, key);
    }

    private void lockClient(int key) {
        jdbc.query("SELECT pg_advisory_xact_lock(2, ?)", result -> {}, key);
    }

    private void store(SecurityEvent event, OffsetDateTime receivedAt) {
        int previous = jdbc.queryForObject("""
                SELECT count(*) FROM (
                    SELECT 1 FROM events WHERE client_ip = ? AND received_at > ? AND received_at <= ? LIMIT ?
                ) recent
                """, Integer.class, event.clientIp(), receivedAt.minus(OFFENDER_WINDOW), receivedAt, OFFENDER_THRESHOLD);
        int score = ThreatScoring.score(event.rule().severity(), event.action(), event.path(),
                previous >= OFFENDER_THRESHOLD);
        ObjectNode payload = mapper.valueToTree(event);
        payload.put("attackType", event.rule().category().attackType);
        payload.put("threatScore", score);
        payload.put("receivedAt", receivedAt.toInstant().toString());
        jdbc.update("""
                INSERT INTO events (event_id, event_timestamp, received_at, config_id, client_ip, path,
                                    category, action, threat_score, payload)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, CAST(? AS jsonb))
                ON CONFLICT (event_id) DO NOTHING
                """, event.eventId(), event.timestamp().atOffset(ZoneOffset.UTC), receivedAt, event.configId(),
                event.clientIp(), event.path(), event.rule().category().name(), event.action().name(), score,
                payload.toString());
    }
}
