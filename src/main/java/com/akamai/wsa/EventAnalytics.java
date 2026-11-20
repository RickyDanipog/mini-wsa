package com.akamai.wsa;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

@Repository
class EventAnalytics {
    private final NamedParameterJdbcTemplate jdbc;
    private final ObjectMapper mapper;

    EventAnalytics(NamedParameterJdbcTemplate jdbc, ObjectMapper mapper) {
        this.jdbc = jdbc;
        this.mapper = mapper;
    }

    @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
    public Summary summary(Integer configId, TimeRange range) {
        Filters filters = new Filters(configId, range);
        String events = " FROM events" + filters.where;
        var parameters = filters.parameters;

        long total = jdbc.queryForObject("SELECT count(*)" + events, parameters, Long.class);
        Map<String, CategoryStats> categories = new LinkedHashMap<>();
        jdbc.query("SELECT category, count(*), round(avg(threat_score), 1)" + events
                + " GROUP BY category ORDER BY category", parameters, row -> {
            categories.put(row.getString(1), new CategoryStats(row.getLong(2), row.getBigDecimal(3)));
        });
        Map<String, Long> actions = new LinkedHashMap<>();
        jdbc.query("SELECT action, count(*)" + events + " GROUP BY action ORDER BY action", parameters, row -> {
            actions.put(row.getString(1), row.getLong(2));
        });
        List<Attacker> attackers = jdbc.query(
                "SELECT client_ip, count(*) AS frequency, round(avg(threat_score), 1)" + events
                        + " GROUP BY client_ip ORDER BY frequency DESC, client_ip LIMIT 10", parameters,
                (row, index) -> new Attacker(row.getString(1), row.getLong(2), row.getBigDecimal(3)));
        List<Target> targets = jdbc.query("SELECT path, count(*) AS frequency" + events
                        + " GROUP BY path ORDER BY frequency DESC, path LIMIT 10", parameters,
                (row, index) -> new Target(row.getString(1), row.getLong(2)));
        return new Summary(configId, range, total, categories, actions, attackers, targets);
    }

    @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
    public Samples samples(Integer configId, TimeRange range, SecurityEvent.Category category,
                           SecurityEvent.Action action, int limit, long offset) {
        Filters filters = new Filters(configId, range);
        filters.add("category", "=", category == null ? null : category.name());
        filters.add("action", "=", action == null ? null : action.name());
        String events = " FROM events" + filters.where;
        var parameters = filters.parameters.addValue("limit", limit).addValue("offset", offset);
        long total = jdbc.queryForObject("SELECT count(*)" + events, parameters, Long.class);
        List<JsonNode> results = jdbc.query("SELECT payload" + events
                        + " ORDER BY event_timestamp DESC, event_id LIMIT :limit OFFSET :offset", parameters,
                (row, index) -> parsePayload(row.getString(1)));
        return new Samples(total, limit, offset, results);
    }

    TimeSeries timeSeries(Integer configId, TimeRange range, String interval, int seconds) {
        Filters filters = new Filters(configId, range);
        List<Bucket> buckets = jdbc.query("""
                SELECT date_bin(make_interval(secs => :seconds), event_timestamp,
                                TIMESTAMPTZ '1970-01-01 00:00:00+00') AS bucket, count(*)
                FROM events
                """ + filters.where + " GROUP BY bucket ORDER BY bucket",
                filters.parameters.addValue("seconds", seconds),
                (row, index) -> new Bucket(row.getObject(1, OffsetDateTime.class).toInstant(), row.getLong(2)));
        return new TimeSeries(configId, range, interval, buckets);
    }

    AlertRule defineAlert(SecurityEvent.Category category, int threshold, int windowMinutes) {
        AlertRule rule = new AlertRule(UUID.randomUUID(), category, threshold, windowMinutes);
        jdbc.update("""
                INSERT INTO alert_rules (id, category, threshold, window_minutes)
                VALUES (:id, :category, :threshold, :window)
                """, new MapSqlParameterSource("id", rule.id()).addValue("category", category.name())
                .addValue("threshold", threshold).addValue("window", windowMinutes));
        return rule;
    }

    AlertEvaluations evaluateAlerts(Instant asOf) {
        List<Alert> alerts = jdbc.query("""
                SELECT r.id, r.category, r.threshold, r.window_minutes,
                       (SELECT count(*) FROM events e
                        WHERE e.category = r.category AND e.event_timestamp <= :asOf
                          AND e.event_timestamp >= :asOf - make_interval(mins => r.window_minutes)) AS frequency
                FROM alert_rules r ORDER BY r.id
                """, new MapSqlParameterSource("asOf", asOf.atOffset(ZoneOffset.UTC)), (row, index) -> {
            int threshold = row.getInt("threshold");
            int minutes = row.getInt("window_minutes");
            long count = row.getLong("frequency");
            return new Alert(row.getObject("id", UUID.class), SecurityEvent.Category.valueOf(row.getString("category")),
                    threshold, minutes, count, count > threshold,
                    new TimeRange(asOf.minus(Duration.ofMinutes(minutes)), asOf));
        });
        return new AlertEvaluations(asOf, alerts);
    }

    private JsonNode parsePayload(String json) {
        try {
            return mapper.readTree(json);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Could not read stored event", exception);
        }
    }

    private static final class Filters {
        private final MapSqlParameterSource parameters = new MapSqlParameterSource();
        private String where = "";

        private Filters(Integer configId, TimeRange range) {
            add("config_id", "=", configId);
            add("event_timestamp", ">=", "from", range.from());
            add("event_timestamp", "<=", "to", range.to());
        }

        private void add(String column, String operator, Object value) {
            add(column, operator, column, value);
        }

        private void add(String column, String operator, String name, Object value) {
            if (value != null) {
                where += (where.isEmpty() ? " WHERE " : " AND ") + column + " " + operator + " :" + name;
                parameters.addValue(name, value instanceof Instant instant ? instant.atOffset(ZoneOffset.UTC) : value);
            }
        }
    }

    record TimeRange(Instant from, Instant to) {}
    record CategoryStats(long count, BigDecimal avgThreatScore) {}
    record Attacker(String clientIp, long count, BigDecimal avgThreatScore) {}
    record Target(String path, long count) {}
    record Summary(Integer configId, TimeRange timeRange, long totalEvents,
                   Map<String, CategoryStats> byCategory, Map<String, Long> byAction,
                   List<Attacker> topAttackers, List<Target> topTargetedPaths) {}
    record Samples(long total, int limit, long offset, List<JsonNode> results) {}
    record Bucket(Instant bucketStart, long count) {}
    record TimeSeries(Integer configId, TimeRange timeRange, String interval, List<Bucket> buckets) {}
    record AlertRule(UUID id, SecurityEvent.Category category, int threshold, int windowMinutes) {}
    record Alert(UUID ruleId, SecurityEvent.Category category, int threshold, int windowMinutes,
                 long count, boolean firing, TimeRange window) {}
    record AlertEvaluations(Instant asOf, List<Alert> alerts) {}
}
