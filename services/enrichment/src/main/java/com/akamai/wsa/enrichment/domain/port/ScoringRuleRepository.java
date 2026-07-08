package com.akamai.wsa.enrichment.domain.port;

import com.akamai.wsa.enrichment.ruleengine.Rule;

import java.util.List;

public interface ScoringRuleRepository {

    String SCORING_TYPE = "SCORING";

    List<Rule<Integer>> findEnabledRules();

    List<Rule<Integer>> findAll();

    Rule<Integer> save(Rule<Integer> rule);

    void deleteById(String id);
}
