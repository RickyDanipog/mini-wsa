package com.akamai.wsa.enrichment.infrastructure.rules;

import com.akamai.wsa.enrichment.domain.port.ScoringRuleRepository;
import com.akamai.wsa.enrichment.ruleengine.Rule;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import static org.assertj.core.api.Assertions.assertThat;

@Testcontainers
class PostgresScoringRuleRepositoryIT {

    @Container
    static final PostgreSQLContainer<?> POSTGRES_CONTAINER = new PostgreSQLContainer<>("postgres:16-alpine");

    private JdbcTemplate jdbcTemplate;
    private PostgresScoringRuleRepository repository;

    @BeforeEach
    void setUp() {
        DriverManagerDataSource dataSource = new DriverManagerDataSource(
                POSTGRES_CONTAINER.getJdbcUrl(),
                POSTGRES_CONTAINER.getUsername(),
                POSTGRES_CONTAINER.getPassword());
        dataSource.setDriverClassName(POSTGRES_CONTAINER.getDriverClassName());
        jdbcTemplate = new JdbcTemplate(dataSource);
        jdbcTemplate.execute("DROP TABLE IF EXISTS rules");
        repository = new PostgresScoringRuleRepository(jdbcTemplate);
        repository.ensureSchemaAndSeed();
    }

    @Test
    void createsTableAndSeedsEightScoringRows() {
        Long scoringCount = jdbcTemplate.queryForObject(
                "SELECT count(*) FROM rules WHERE type = ?", Long.class, ScoringRuleRepository.SCORING_TYPE);
        assertThat(scoringCount).isEqualTo(8L);

        assertThat(repository.findEnabledRules()).hasSize(8);
        assertThat(repository.findEnabledRules()).extracting(Rule::type)
                .containsOnly(ScoringRuleRepository.SCORING_TYPE);
    }

    @Test
    void seedsAreOrderedByPriority() {
        assertThat(repository.findEnabledRules())
                .isSortedAccordingTo((left, right) -> Integer.compare(left.priority(), right.priority()));
    }

    @Test
    void excludesDisabledRules() {
        jdbcTemplate.update("UPDATE rules SET enabled = false WHERE id = ?", "severity-critical");

        assertThat(repository.findEnabledRules())
                .extracting(Rule::id)
                .doesNotContain("severity-critical")
                .hasSize(7);
    }

    @Test
    void doesNotReturnRulesOfADifferentType() {
        jdbcTemplate.update("""
                INSERT INTO rules (id, type, title, fact_key, operator, operand, output, priority, enabled)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)
                """,
                "routing-1", "ROUTING", "Routing rule", "path", "STARTS_WITH", "/api", "queue-a", 5, true);

        assertThat(repository.findEnabledRules())
                .extracting(Rule::id)
                .doesNotContain("routing-1")
                .hasSize(8);
    }

    @Test
    void reflectsEditsToOutputAndOperand() {
        jdbcTemplate.update("UPDATE rules SET output = ?, operand = ? WHERE id = ?",
                "99", "/danger", "sensitive-path");

        Rule<Integer> edited = repository.findEnabledRules().stream()
                .filter(rule -> rule.id().equals("sensitive-path"))
                .findFirst()
                .orElseThrow();

        assertThat(edited.output()).isEqualTo(99);
        assertThat(edited.condition().operand()).isEqualTo("/danger");
    }
}
