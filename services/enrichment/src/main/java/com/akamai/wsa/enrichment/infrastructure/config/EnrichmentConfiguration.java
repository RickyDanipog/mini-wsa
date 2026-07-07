package com.akamai.wsa.enrichment.infrastructure.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Clock;

@Configuration
public class EnrichmentConfiguration {

    @Bean
    public Clock clock() {
        return Clock.systemUTC();
    }
}
