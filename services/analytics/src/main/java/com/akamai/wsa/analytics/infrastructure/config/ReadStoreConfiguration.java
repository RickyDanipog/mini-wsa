package com.akamai.wsa.analytics.infrastructure.config;

import com.akamai.wsa.analytics.domain.port.AnalyticsReadStore;
import com.akamai.wsa.analytics.infrastructure.persistence.inmemory.InMemoryAnalyticsReadStore;
import com.akamai.wsa.analytics.infrastructure.seed.DevDataSeed;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;

@Configuration
public class ReadStoreConfiguration {

    // Default runnable store for dry runs / sanity — active unless the `mongo` profile is on.
    @Bean
    @Profile("!mongo")
    public AnalyticsReadStore inMemoryAnalyticsReadStore() {
        return new InMemoryAnalyticsReadStore(DevDataSeed.seedEvents());
    }
}
