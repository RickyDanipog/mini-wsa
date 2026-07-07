package com.akamai.wsa.enrichment.infrastructure.config;

import com.akamai.wsa.enrichment.domain.service.ActionRule;
import com.akamai.wsa.enrichment.domain.service.AttackTypeClassifier;
import com.akamai.wsa.enrichment.domain.service.DefaultAttackTypeClassifier;
import com.akamai.wsa.enrichment.domain.service.RepeatOffenderRule;
import com.akamai.wsa.enrichment.domain.service.RuleBasedThreatScoreCalculator;
import com.akamai.wsa.enrichment.domain.service.SensitivePathRule;
import com.akamai.wsa.enrichment.domain.service.SeverityRule;
import com.akamai.wsa.enrichment.domain.service.ThreatScoreCalculator;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Clock;
import java.util.List;

/** Wires the pure domain services (which import no Spring) as Spring beans. */
@Configuration
public class EnrichmentConfiguration {

    @Bean
    public Clock clock() {
        return Clock.systemUTC();
    }

    @Bean
    public AttackTypeClassifier attackTypeClassifier() {
        return new DefaultAttackTypeClassifier();
    }

    @Bean
    public ThreatScoreCalculator threatScoreCalculator() {
        return new RuleBasedThreatScoreCalculator(List.of(
                new SeverityRule(),
                new ActionRule(),
                new SensitivePathRule(),
                new RepeatOffenderRule()));
    }
}
