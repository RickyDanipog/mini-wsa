package com.akamai.wsa.enrichment.infrastructure.rules;

import com.akamai.wsa.enrichment.domain.port.ScoringRuleRepository;
import com.akamai.wsa.enrichment.ruleengine.Rule;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
@ConditionalOnProperty(name = "wsa.rules", havingValue = "inmemory", matchIfMissing = true)
public class InMemoryScoringRuleRepository implements ScoringRuleRepository {

    @Override
    public List<Rule<Integer>> findEnabledRules() {
        return DefaultScoringRules.asList();
    }
}
