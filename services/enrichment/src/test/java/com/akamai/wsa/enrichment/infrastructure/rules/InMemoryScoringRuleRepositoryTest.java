package com.akamai.wsa.enrichment.infrastructure.rules;

import com.akamai.wsa.enrichment.domain.port.ScoringRuleRepository;
import com.akamai.wsa.enrichment.ruleengine.Rule;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class InMemoryScoringRuleRepositoryTest {

    private final ScoringRuleRepository repository = new InMemoryScoringRuleRepository();

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
}
