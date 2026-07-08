package com.akamai.wsa.enrichment.infrastructure.config;

import com.akamai.wsa.enrichment.domain.port.ScoringRuleRepository;
import com.akamai.wsa.enrichment.domain.service.RuleEngineThreatScoreCalculator;
import com.akamai.wsa.enrichment.domain.service.ThreatScoreCalculator;
import com.akamai.wsa.enrichment.ruleengine.RuleEngine;
import com.akamai.wsa.enrichment.ruleengine.RuleEvaluator;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class RuleEngineConfiguration {

    @Bean
    public RuleEvaluator ruleEvaluator() {
        return new RuleEvaluator();
    }

    @Bean
    public RuleEngine ruleEngine(RuleEvaluator ruleEvaluator) {
        return new RuleEngine(ruleEvaluator);
    }

    @Bean
    public ThreatScoreCalculator threatScoreCalculator(ScoringRuleRepository scoringRuleRepository,
                                                       RuleEngine ruleEngine) {
        return new RuleEngineThreatScoreCalculator(scoringRuleRepository, ruleEngine);
    }
}
