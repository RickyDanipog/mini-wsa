package com.akamai.wsa.analytics.infrastructure.config;

import com.akamai.wsa.analytics.infrastructure.ratelimit.RateLimitFilter;
import com.akamai.wsa.analytics.infrastructure.ratelimit.RateLimitProperties;
import com.akamai.wsa.analytics.infrastructure.ratelimit.RateLimiter;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Clock;
import java.time.Duration;

@Configuration
@EnableConfigurationProperties(RateLimitProperties.class)
public class RateLimitConfiguration {

    @Bean
    @ConditionalOnMissingBean
    public Clock systemUtcClock() {
        return Clock.systemUTC();
    }

    @Bean
    public RateLimiter rateLimiter(RateLimitProperties properties, Clock clock) {
        return new RateLimiter(properties.maxRequests(), Duration.ofSeconds(properties.windowSeconds()), clock);
    }

    @Bean
    public FilterRegistrationBean<RateLimitFilter> rateLimitFilterRegistration(
            RateLimiter rateLimiter, RateLimitProperties properties, Clock clock, ObjectMapper objectMapper) {
        FilterRegistrationBean<RateLimitFilter> registration =
                new FilterRegistrationBean<>(new RateLimitFilter(rateLimiter, properties, clock, objectMapper));
        registration.addUrlPatterns("/v1/*");
        return registration;
    }
}
