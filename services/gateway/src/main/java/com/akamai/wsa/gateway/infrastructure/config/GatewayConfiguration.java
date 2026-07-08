package com.akamai.wsa.gateway.infrastructure.config;

import com.akamai.wsa.gateway.application.EventRequestMapper;
import com.akamai.wsa.gateway.application.EventRequestReader;
import com.akamai.wsa.gateway.application.IngestEvents;
import com.akamai.wsa.gateway.application.IngestEventsService;
import com.akamai.wsa.gateway.infrastructure.messaging.RawEventPublisher;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.validation.Validator;
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

    @Bean
    public EventRequestReader eventRequestReader(ObjectMapper objectMapper) {
        return new EventRequestReader(objectMapper);
    }

    @Bean
    public IngestEvents ingestEvents(Validator validator, EventRequestMapper eventRequestMapper,
                                     RawEventPublisher rawEventPublisher, Clock clock) {
        return new IngestEventsService(validator, eventRequestMapper, rawEventPublisher, clock);
    }
}
