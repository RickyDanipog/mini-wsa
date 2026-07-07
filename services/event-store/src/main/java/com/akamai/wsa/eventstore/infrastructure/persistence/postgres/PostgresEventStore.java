package com.akamai.wsa.eventstore.infrastructure.persistence.postgres;

import com.akamai.wsa.contracts.Action;
import com.akamai.wsa.contracts.AttackCategory;
import com.akamai.wsa.contracts.Severity;
import com.akamai.wsa.eventstore.domain.model.StoredEvent;
import com.akamai.wsa.eventstore.domain.model.StoredGeoLocation;
import com.akamai.wsa.eventstore.domain.model.StoredRule;
import com.akamai.wsa.eventstore.domain.port.EventStore;
import jakarta.annotation.PostConstruct;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;

@Repository
@ConditionalOnProperty(name = "wsa.storage", havingValue = "postgres")
public class PostgresEventStore implements EventStore {

    private static final String CREATE_TABLE_STATEMENT = """
            CREATE TABLE IF NOT EXISTS events (
                event_id VARCHAR(128) PRIMARY KEY, timestamp TIMESTAMPTZ NOT NULL, config_id INTEGER NOT NULL,
                policy_id VARCHAR(128), client_ip VARCHAR(64) NOT NULL, hostname VARCHAR(255), path VARCHAR(2048),
                method VARCHAR(16), status_code INTEGER, user_agent TEXT, rule_id VARCHAR(128), rule_name VARCHAR(255),
                rule_message TEXT, severity VARCHAR(16), category VARCHAR(32), action VARCHAR(16) NOT NULL,
                geo_country VARCHAR(64), geo_city VARCHAR(128), request_size BIGINT, response_size BIGINT,
                attack_type VARCHAR(128), threat_score INTEGER NOT NULL, received_at TIMESTAMPTZ)
            """;

    private static final String CREATE_CONFIG_TIMESTAMP_INDEX_STATEMENT =
            "CREATE INDEX IF NOT EXISTS idx_events_config_ts ON events (config_id, timestamp DESC)";

    private static final String CREATE_CLIENT_IP_TIMESTAMP_INDEX_STATEMENT =
            "CREATE INDEX IF NOT EXISTS idx_events_clientip_ts ON events (client_ip, timestamp DESC)";

    private static final String CREATE_TIMESTAMP_INDEX_STATEMENT =
            "CREATE INDEX IF NOT EXISTS idx_events_ts ON events (timestamp DESC)";

    private static final String INSERT_STATEMENT = """
            INSERT INTO events (
                event_id, timestamp, config_id, policy_id, client_ip, hostname, path, method, status_code,
                user_agent, rule_id, rule_name, rule_message, severity, category, action, geo_country, geo_city,
                request_size, response_size, attack_type, threat_score, received_at)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
            ON CONFLICT (event_id) DO NOTHING
            """;

    private static final String COUNT_STATEMENT = "SELECT count(*) FROM events";

    private static final String FIND_BY_CONFIG_ID_STATEMENT = "SELECT * FROM events WHERE config_id = ?";

    private final JdbcTemplate jdbcTemplate;
    private final RowMapper<StoredEvent> storedEventRowMapper = new StoredEventRowMapper();

    public PostgresEventStore(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @PostConstruct
    void ensureSchema() {
        jdbcTemplate.execute(CREATE_TABLE_STATEMENT);
        jdbcTemplate.execute(CREATE_CONFIG_TIMESTAMP_INDEX_STATEMENT);
        jdbcTemplate.execute(CREATE_CLIENT_IP_TIMESTAMP_INDEX_STATEMENT);
        jdbcTemplate.execute(CREATE_TIMESTAMP_INDEX_STATEMENT);
    }

    @Override
    public void saveAll(List<StoredEvent> storedEvents) {
        jdbcTemplate.batchUpdate(INSERT_STATEMENT, storedEvents, storedEvents.size(),
                (preparedStatement, storedEvent) -> {
                    StoredRule rule = storedEvent.rule();
                    StoredGeoLocation geoLocation = storedEvent.geoLocation();
                    preparedStatement.setString(1, storedEvent.eventId());
                    preparedStatement.setObject(2, toOffsetDateTime(storedEvent.timestamp()));
                    preparedStatement.setInt(3, storedEvent.configId());
                    preparedStatement.setString(4, storedEvent.policyId());
                    preparedStatement.setString(5, storedEvent.clientIp());
                    preparedStatement.setString(6, storedEvent.hostname());
                    preparedStatement.setString(7, storedEvent.path());
                    preparedStatement.setString(8, storedEvent.method());
                    preparedStatement.setInt(9, storedEvent.statusCode());
                    preparedStatement.setString(10, storedEvent.userAgent());
                    preparedStatement.setString(11, rule == null ? null : rule.id());
                    preparedStatement.setString(12, rule == null ? null : rule.name());
                    preparedStatement.setString(13, rule == null ? null : rule.message());
                    preparedStatement.setString(14, rule == null || rule.severity() == null ? null : rule.severity().name());
                    preparedStatement.setString(15, rule == null || rule.category() == null ? null : rule.category().name());
                    preparedStatement.setString(16, storedEvent.action() == null ? null : storedEvent.action().name());
                    preparedStatement.setString(17, geoLocation == null ? null : geoLocation.country());
                    preparedStatement.setString(18, geoLocation == null ? null : geoLocation.city());
                    preparedStatement.setLong(19, storedEvent.requestSize());
                    preparedStatement.setLong(20, storedEvent.responseSize());
                    preparedStatement.setString(21, storedEvent.attackType());
                    preparedStatement.setInt(22, storedEvent.threatScore());
                    preparedStatement.setObject(23, toOffsetDateTime(storedEvent.receivedAt()));
                });
    }

    @Override
    public long countAll() {
        Long count = jdbcTemplate.queryForObject(COUNT_STATEMENT, Long.class);
        return count == null ? 0L : count;
    }

    @Override
    public List<StoredEvent> findByConfigId(int configId) {
        return jdbcTemplate.query(FIND_BY_CONFIG_ID_STATEMENT, storedEventRowMapper, configId);
    }

    private static OffsetDateTime toOffsetDateTime(Instant instant) {
        return instant == null ? null : OffsetDateTime.ofInstant(instant, ZoneOffset.UTC);
    }

    private static final class StoredEventRowMapper implements RowMapper<StoredEvent> {

        @Override
        public StoredEvent mapRow(ResultSet resultSet, int rowNumber) throws SQLException {
            return new StoredEvent(
                    resultSet.getString("event_id"),
                    resultSet.getObject("timestamp", OffsetDateTime.class).toInstant(),
                    resultSet.getInt("config_id"),
                    resultSet.getString("policy_id"),
                    resultSet.getString("client_ip"),
                    resultSet.getString("hostname"),
                    resultSet.getString("path"),
                    resultSet.getString("method"),
                    resultSet.getInt("status_code"),
                    resultSet.getString("user_agent"),
                    mapRule(resultSet),
                    mapAction(resultSet),
                    mapGeoLocation(resultSet),
                    resultSet.getLong("request_size"),
                    resultSet.getLong("response_size"),
                    resultSet.getString("attack_type"),
                    resultSet.getInt("threat_score"),
                    mapReceivedAt(resultSet));
        }

        private StoredRule mapRule(ResultSet resultSet) throws SQLException {
            String ruleId = resultSet.getString("rule_id");
            String ruleName = resultSet.getString("rule_name");
            String ruleMessage = resultSet.getString("rule_message");
            String severity = resultSet.getString("severity");
            String category = resultSet.getString("category");
            if (ruleId == null && ruleName == null && ruleMessage == null && severity == null && category == null) {
                return null;
            }
            return new StoredRule(
                    ruleId,
                    ruleName,
                    ruleMessage,
                    severity == null ? null : Severity.valueOf(severity),
                    category == null ? null : AttackCategory.valueOf(category));
        }

        private Action mapAction(ResultSet resultSet) throws SQLException {
            String action = resultSet.getString("action");
            return action == null ? null : Action.valueOf(action);
        }

        private StoredGeoLocation mapGeoLocation(ResultSet resultSet) throws SQLException {
            String country = resultSet.getString("geo_country");
            String city = resultSet.getString("geo_city");
            if (country == null && city == null) {
                return null;
            }
            return new StoredGeoLocation(country, city);
        }

        private Instant mapReceivedAt(ResultSet resultSet) throws SQLException {
            OffsetDateTime receivedAt = resultSet.getObject("received_at", OffsetDateTime.class);
            return receivedAt == null ? null : receivedAt.toInstant();
        }
    }
}
