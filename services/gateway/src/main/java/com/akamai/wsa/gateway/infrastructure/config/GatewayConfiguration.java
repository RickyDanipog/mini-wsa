package com.akamai.wsa.gateway.infrastructure.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Clock;

@Configuration
public class GatewayConfiguration {

    @Bean
    public Clock clock() {
        return Clock.systemUTC();
    }
}
