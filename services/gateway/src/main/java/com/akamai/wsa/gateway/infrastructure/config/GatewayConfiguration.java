package com.akamai.wsa.gateway.infrastructure.config;

import com.akamai.wsa.gateway.application.EventRequestMapper;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Clock;

@Configuration
public class GatewayConfiguration {

    @Bean
    public Clock clock() {
        return Clock.systemUTC();
    }

    @Bean
    public EventRequestMapper eventRequestMapper() {
        return new EventRequestMapper();
    }
}
