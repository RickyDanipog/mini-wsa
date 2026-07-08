package com.akamai.wsa.enrichment.infrastructure.config;

import com.akamai.wsa.enrichment.domain.port.ScoringRuleRepository;
import com.akamai.wsa.enrichment.domain.service.RuleEngineThreatScoreCalculator;
import com.akamai.wsa.enrichment.domain.service.ThreatScoreCalculator;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class RuleEngineConfiguration {

    @Bean
    public ThreatScoreCalculator threatScoreCalculator(ScoringRuleRepository scoringRuleRepository) {
        return new RuleEngineThreatScoreCalculator(scoringRuleRepository);
    }
}
