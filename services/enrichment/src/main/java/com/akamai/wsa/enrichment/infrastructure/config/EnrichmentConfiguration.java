package com.akamai.wsa.enrichment.infrastructure.config;

import com.akamai.wsa.enrichment.domain.service.AttackTypeClassifier;
import com.akamai.wsa.enrichment.domain.service.DefaultAttackTypeClassifier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Clock;

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
}
