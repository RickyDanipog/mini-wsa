package com.akamai.wsa.enrichment.infrastructure.rules;

import com.akamai.wsa.enrichment.domain.port.ScoringRuleRepository;
import com.akamai.wsa.enrichment.ruleengine.Rule;
import com.akamai.wsa.enrichment.ruleengine.RuleCondition;
import com.akamai.wsa.enrichment.ruleengine.RuleOperator;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class InMemoryScoringRuleRepositoryTest {

    private final ScoringRuleRepository repository = new InMemoryScoringRuleRepository();

    private static Rule<Integer> rule(String id, boolean enabled, int points) {
        return new Rule<>(id, ScoringRuleRepository.SCORING_TYPE, id, 99, enabled,
                new RuleCondition("action", RuleOperator.EQUAL_TO, "DENY"), points);
    }

    @Test
    void returnsTheEightDefaultRules() {
        List<Rule<Integer>> rules = repository.findEnabledRules();

        assertThat(rules).hasSize(8);
        assertThat(rules).extracting(Rule::id).containsExactlyInAnyOrder(
                "severity-critical", "severity-high", "severity-medium", "severity-low",
                "action-deny", "action-alert", "sensitive-path", "repeat-offender");
    }

    @Test
    void everyDefaultRuleIsScoringTyped() {
        assertThat(repository.findEnabledRules())
                .extracting(Rule::type)
                .containsOnly(ScoringRuleRepository.SCORING_TYPE);
    }

    @Test
    void saveAddsANewRuleThatFindAllReturns() {
        repository.save(rule("custom", true, 25));

        assertThat(repository.findAll()).extracting(Rule::id).contains("custom");
        assertThat(repository.findEnabledRules()).extracting(Rule::id).contains("custom");
    }

    @Test
    void saveWithAnExistingIdReplacesTheRule() {
        repository.save(rule("severity-critical", true, 5));

        Rule<Integer> updated = repository.findAll().stream()
                .filter(rule -> rule.id().equals("severity-critical")).findFirst().orElseThrow();
        assertThat(updated.output()).isEqualTo(5);
        assertThat(repository.findAll()).hasSize(8);
    }

    @Test
    void findAllIncludesDisabledRulesButFindEnabledDoesNot() {
        repository.save(rule("dormant", false, 10));

        assertThat(repository.findAll()).extracting(Rule::id).contains("dormant");
        assertThat(repository.findEnabledRules()).extracting(Rule::id).doesNotContain("dormant");
    }

    @Test
    void deleteByIdRemovesTheRule() {
        repository.deleteById("severity-low");

        assertThat(repository.findAll()).extracting(Rule::id).doesNotContain("severity-low");
        assertThat(repository.findAll()).hasSize(7);
    }
}
