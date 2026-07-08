package com.akamai.wsa.enrichment.infrastructure.rules;

import com.akamai.wsa.enrichment.domain.port.ScoringRuleRepository;
import com.akamai.wsa.enrichment.ruleengine.Rule;
import com.akamai.wsa.enrichment.ruleengine.RuleCondition;
import com.akamai.wsa.enrichment.ruleengine.RuleOperator;
import jakarta.annotation.PostConstruct;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;

@Repository
@ConditionalOnProperty(name = "wsa.rules", havingValue = "postgres")
public class PostgresScoringRuleRepository implements ScoringRuleRepository {

    private static final String CREATE_TABLE_STATEMENT = """
            CREATE TABLE IF NOT EXISTS rules (
                id VARCHAR(64) PRIMARY KEY, type VARCHAR(32) NOT NULL, title VARCHAR(128) NOT NULL,
                fact_key VARCHAR(64) NOT NULL, operator VARCHAR(32) NOT NULL, operand VARCHAR(256) NOT NULL,
                output VARCHAR(64) NOT NULL, priority INT NOT NULL DEFAULT 0, enabled BOOLEAN NOT NULL DEFAULT TRUE)
            """;

    private static final String CREATE_TYPE_ENABLED_INDEX_STATEMENT =
            "CREATE INDEX IF NOT EXISTS idx_rules_type_enabled ON rules(type, enabled)";

    private static final String COUNT_SCORING_STATEMENT =
            "SELECT count(*) FROM rules WHERE type = ?";

    private static final String INSERT_STATEMENT = """
            INSERT INTO rules (id, type, title, fact_key, operator, operand, output, priority, enabled)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)
            """;

    private static final String FIND_ENABLED_STATEMENT = """
            SELECT id, type, title, fact_key, operator, operand, output, priority, enabled
            FROM rules WHERE type = ? AND enabled = true ORDER BY priority
            """;

    private final JdbcTemplate jdbcTemplate;
    private final RowMapper<Rule<Integer>> scoringRuleRowMapper = new ScoringRuleRowMapper();

    public PostgresScoringRuleRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @PostConstruct
    void ensureSchemaAndSeed() {
        jdbcTemplate.execute(CREATE_TABLE_STATEMENT);
        jdbcTemplate.execute(CREATE_TYPE_ENABLED_INDEX_STATEMENT);
        Long scoringRuleCount = jdbcTemplate.queryForObject(COUNT_SCORING_STATEMENT, Long.class, SCORING_TYPE);
        if (scoringRuleCount == null || scoringRuleCount == 0L) {
            seedDefaults();
        }
    }

    private void seedDefaults() {
        List<Rule<Integer>> defaults = DefaultScoringRules.asList();
        jdbcTemplate.batchUpdate(INSERT_STATEMENT, defaults, defaults.size(),
                (preparedStatement, rule) -> {
                    RuleCondition condition = rule.condition();
                    preparedStatement.setString(1, rule.id());
                    preparedStatement.setString(2, rule.type());
                    preparedStatement.setString(3, rule.title());
                    preparedStatement.setString(4, condition.factKey());
                    preparedStatement.setString(5, condition.operator().name());
                    preparedStatement.setString(6, condition.operand());
                    preparedStatement.setString(7, Integer.toString(rule.output()));
                    preparedStatement.setInt(8, rule.priority());
                    preparedStatement.setBoolean(9, rule.enabled());
                });
    }

    @Override
    public List<Rule<Integer>> findEnabledRules() {
        return jdbcTemplate.query(FIND_ENABLED_STATEMENT, scoringRuleRowMapper, SCORING_TYPE);
    }

    private static final class ScoringRuleRowMapper implements RowMapper<Rule<Integer>> {

        @Override
        public Rule<Integer> mapRow(ResultSet resultSet, int rowNumber) throws SQLException {
            RuleCondition condition = new RuleCondition(
                    resultSet.getString("fact_key"),
                    RuleOperator.valueOf(resultSet.getString("operator")),
                    resultSet.getString("operand"));
            return new Rule<>(
                    resultSet.getString("id"),
                    resultSet.getString("type"),
                    resultSet.getString("title"),
                    resultSet.getInt("priority"),
                    resultSet.getBoolean("enabled"),
                    condition,
                    Integer.parseInt(resultSet.getString("output")));
        }
    }
}
