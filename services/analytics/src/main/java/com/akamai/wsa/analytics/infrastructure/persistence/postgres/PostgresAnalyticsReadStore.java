package com.akamai.wsa.analytics.infrastructure.persistence.postgres;

import com.akamai.wsa.analytics.domain.model.EnrichedEventView;
import com.akamai.wsa.analytics.domain.port.AnalyticsReadStore;
import com.akamai.wsa.analytics.domain.query.AttackerStatistics;
import com.akamai.wsa.analytics.domain.query.CategoryStatistics;
import com.akamai.wsa.analytics.domain.query.EventSamplesPage;
import com.akamai.wsa.analytics.domain.query.PathStatistics;
import com.akamai.wsa.analytics.domain.query.SampleQuery;
import com.akamai.wsa.analytics.domain.query.StatisticsQuery;
import com.akamai.wsa.analytics.domain.query.StatisticsSummary;
import com.akamai.wsa.analytics.domain.query.TimeRange;
import com.akamai.wsa.analytics.domain.query.TimeSeriesBucket;
import com.akamai.wsa.analytics.domain.query.TimeSeriesQuery;
import com.akamai.wsa.analytics.domain.query.TimeSeriesResult;
import com.akamai.wsa.contracts.Action;
import com.akamai.wsa.contracts.AttackCategory;
import com.akamai.wsa.contracts.Severity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

public class PostgresAnalyticsReadStore implements AnalyticsReadStore {

    private final JdbcTemplate jdbcTemplate;
    private final RowMapper<EnrichedEventView> enrichedEventViewRowMapper = new EnrichedEventViewRowMapper();

    public PostgresAnalyticsReadStore(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public StatisticsSummary summarize(StatisticsQuery statisticsQuery) {
        WhereClause whereClause = buildWhereClause(
                statisticsQuery.configId(), statisticsQuery.timeRange(), null, null);
        Object[] arguments = whereClause.arguments().toArray();

        long totalEvents = countEvents(whereClause, arguments);
        Map<AttackCategory, CategoryStatistics> byCategory = queryByCategory(whereClause, arguments);
        Map<Action, Long> byAction = queryByAction(whereClause, arguments);
        List<AttackerStatistics> topAttackers = queryTopAttackers(whereClause, arguments);
        List<PathStatistics> topTargetedPaths = queryTopTargetedPaths(whereClause, arguments);

        return new StatisticsSummary(statisticsQuery.configId(), statisticsQuery.timeRange(),
                totalEvents, byCategory, byAction, topAttackers, topTargetedPaths);
    }

    @Override
    public EventSamplesPage findSamples(SampleQuery sampleQuery) {
        WhereClause whereClause = buildWhereClause(
                sampleQuery.configId(), sampleQuery.timeRange(), sampleQuery.category(), sampleQuery.action());
        Object[] filterArguments = whereClause.arguments().toArray();

        long total = countEvents(whereClause, filterArguments);

        List<Object> pageArguments = new ArrayList<>(whereClause.arguments());
        pageArguments.add(sampleQuery.offset());
        pageArguments.add(sampleQuery.limit());

        List<EnrichedEventView> events = jdbcTemplate.query(
                "SELECT * FROM events" + whereClause.sql() + " ORDER BY timestamp DESC OFFSET ? LIMIT ?",
                enrichedEventViewRowMapper,
                pageArguments.toArray());

        return new EventSamplesPage(total, sampleQuery.limit(), sampleQuery.offset(), events);
    }

    @Override
    public TimeSeriesResult timeSeries(TimeSeriesQuery timeSeriesQuery) {
        WhereClause whereClause = buildWhereClause(
                timeSeriesQuery.configId(), timeSeriesQuery.timeRange(), null, null);
        Object[] arguments = whereClause.arguments().toArray();
        long intervalSeconds = timeSeriesQuery.interval().duration().getSeconds();

        List<TimeSeriesBucket> buckets = jdbcTemplate.query(
                "SELECT to_timestamp(floor(extract(epoch from timestamp) / " + intervalSeconds + ") * "
                        + intervalSeconds + ") AS bucket_start, count(*) AS bucket_count"
                        + " FROM events" + whereClause.sql()
                        + " GROUP BY bucket_start ORDER BY bucket_start",
                (resultSet, rowNumber) -> new TimeSeriesBucket(
                        resultSet.getObject("bucket_start", OffsetDateTime.class).toInstant(),
                        resultSet.getLong("bucket_count")),
                arguments);

        return new TimeSeriesResult(timeSeriesQuery.configId(), timeSeriesQuery.timeRange(),
                timeSeriesQuery.interval(), buckets);
    }

    private long countEvents(WhereClause whereClause, Object[] arguments) {
        Long count = jdbcTemplate.queryForObject(
                "SELECT count(*) FROM events" + whereClause.sql(), Long.class, arguments);
        return count == null ? 0L : count;
    }

    private Map<AttackCategory, CategoryStatistics> queryByCategory(WhereClause whereClause, Object[] arguments) {
        Map<AttackCategory, CategoryStatistics> byCategory = new EnumMap<>(AttackCategory.class);
        jdbcTemplate.query(
                "SELECT category, count(*) AS event_count, avg(threat_score) AS average_threat_score"
                        + " FROM events" + whereClause.sql() + " GROUP BY category",
                resultSet -> {
                    String categoryName = resultSet.getString("category");
                    if (categoryName != null) {
                        byCategory.put(AttackCategory.valueOf(categoryName), new CategoryStatistics(
                                resultSet.getLong("event_count"),
                                resultSet.getDouble("average_threat_score")));
                    }
                },
                arguments);
        return byCategory;
    }

    private Map<Action, Long> queryByAction(WhereClause whereClause, Object[] arguments) {
        Map<Action, Long> byAction = new EnumMap<>(Action.class);
        jdbcTemplate.query(
                "SELECT action, count(*) AS event_count FROM events" + whereClause.sql() + " GROUP BY action",
                resultSet -> {
                    String actionName = resultSet.getString("action");
                    if (actionName != null) {
                        byAction.put(Action.valueOf(actionName), resultSet.getLong("event_count"));
                    }
                },
                arguments);
        return byAction;
    }

    private List<AttackerStatistics> queryTopAttackers(WhereClause whereClause, Object[] arguments) {
        return jdbcTemplate.query(
                "SELECT client_ip, count(*) AS event_count, avg(threat_score) AS average_threat_score"
                        + " FROM events" + whereClause.sql()
                        + " GROUP BY client_ip ORDER BY event_count DESC, client_ip ASC LIMIT "
                        + StatisticsSummary.TOP_LIMIT,
                (resultSet, rowNumber) -> new AttackerStatistics(
                        resultSet.getString("client_ip"),
                        resultSet.getLong("event_count"),
                        resultSet.getDouble("average_threat_score")),
                arguments);
    }

    private List<PathStatistics> queryTopTargetedPaths(WhereClause whereClause, Object[] arguments) {
        return jdbcTemplate.query(
                "SELECT path, count(*) AS event_count FROM events" + whereClause.sql()
                        + " GROUP BY path ORDER BY event_count DESC, path ASC LIMIT " + StatisticsSummary.TOP_LIMIT,
                (resultSet, rowNumber) -> new PathStatistics(
                        resultSet.getString("path"),
                        resultSet.getLong("event_count")),
                arguments);
    }

    private WhereClause buildWhereClause(Integer configId, TimeRange timeRange,
                                         AttackCategory category, Action action) {
        List<String> conditions = new ArrayList<>();
        List<Object> arguments = new ArrayList<>();
        if (configId != null) {
            conditions.add("config_id = ?");
            arguments.add(configId);
        }
        if (timeRange != null) {
            if (timeRange.from() != null) {
                conditions.add("timestamp >= ?");
                arguments.add(toOffsetDateTime(timeRange.from()));
            }
            if (timeRange.to() != null) {
                conditions.add("timestamp <= ?");
                arguments.add(toOffsetDateTime(timeRange.to()));
            }
        }
        if (category != null) {
            conditions.add("category = ?");
            arguments.add(category.name());
        }
        if (action != null) {
            conditions.add("action = ?");
            arguments.add(action.name());
        }
        if (conditions.isEmpty()) {
            return new WhereClause("", arguments);
        }
        return new WhereClause(" WHERE " + String.join(" AND ", conditions), arguments);
    }

    private static OffsetDateTime toOffsetDateTime(Instant instant) {
        return instant == null ? null : OffsetDateTime.ofInstant(instant, ZoneOffset.UTC);
    }

    private record WhereClause(String sql, List<Object> arguments) {
    }

    private static final class EnrichedEventViewRowMapper implements RowMapper<EnrichedEventView> {

        @Override
        public EnrichedEventView mapRow(ResultSet resultSet, int rowNumber) throws SQLException {
            String severity = resultSet.getString("severity");
            String category = resultSet.getString("category");
            String action = resultSet.getString("action");
            return new EnrichedEventView(
                    resultSet.getString("event_id"),
                    toInstant(resultSet.getObject("timestamp", OffsetDateTime.class)),
                    resultSet.getInt("config_id"),
                    resultSet.getString("policy_id"),
                    resultSet.getString("client_ip"),
                    resultSet.getString("hostname"),
                    resultSet.getString("path"),
                    resultSet.getString("method"),
                    resultSet.getInt("status_code"),
                    resultSet.getString("user_agent"),
                    resultSet.getString("rule_id"),
                    resultSet.getString("rule_name"),
                    resultSet.getString("rule_message"),
                    severity == null ? null : Severity.valueOf(severity),
                    category == null ? null : AttackCategory.valueOf(category),
                    action == null ? null : Action.valueOf(action),
                    resultSet.getString("geo_country"),
                    resultSet.getString("geo_city"),
                    resultSet.getLong("request_size"),
                    resultSet.getLong("response_size"),
                    resultSet.getString("attack_type"),
                    resultSet.getInt("threat_score"),
                    toInstant(resultSet.getObject("received_at", OffsetDateTime.class)));
        }

        private static Instant toInstant(OffsetDateTime offsetDateTime) {
            return offsetDateTime == null ? null : offsetDateTime.toInstant();
        }
    }
}
