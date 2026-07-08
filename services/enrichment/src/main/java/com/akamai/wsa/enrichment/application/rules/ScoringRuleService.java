package com.akamai.wsa.enrichment.application.rules;

import com.akamai.wsa.enrichment.domain.port.ScoringRuleRepository;
import com.akamai.wsa.enrichment.ruleengine.Rule;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.UUID;

@Service
public class ScoringRuleService {

    private final ScoringRuleRepository scoringRuleRepository;

    public ScoringRuleService(ScoringRuleRepository scoringRuleRepository) {
        this.scoringRuleRepository = scoringRuleRepository;
    }

    public List<Rule<Integer>> findAll() {
        return scoringRuleRepository.findAll();
    }

    public Rule<Integer> save(Rule<Integer> rule) {
        Rule<Integer> toSave = (rule.id() == null || rule.id().isBlank())
                ? new Rule<>(UUID.randomUUID().toString(), rule.type(), rule.title(),
                        rule.priority(), rule.enabled(), rule.condition(), rule.output())
                : rule;
        return scoringRuleRepository.save(toSave);
    }

    public void delete(String id) {
        scoringRuleRepository.deleteById(id);
    }
}
