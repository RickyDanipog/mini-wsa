package com.akamai.wsa.analytics.infrastructure.config;

import com.akamai.wsa.analytics.domain.port.AnalyticsReadStore;
import com.akamai.wsa.analytics.infrastructure.persistence.inmemory.InMemoryAnalyticsReadStore;
import com.akamai.wsa.analytics.infrastructure.persistence.mongo.MongoAnalyticsReadStore;
import com.akamai.wsa.analytics.infrastructure.seed.DevDataSeed;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.mongodb.core.MongoTemplate;

@Configuration
public class ReadStoreConfiguration {

    @Bean
    @ConditionalOnProperty(name = "wsa.storage", havingValue = "inmemory", matchIfMissing = true)
    public AnalyticsReadStore inMemoryAnalyticsReadStore() {
        return new InMemoryAnalyticsReadStore(DevDataSeed.seedEvents());
    }

    @Bean
    @ConditionalOnProperty(name = "wsa.storage", havingValue = "mongo")
    public AnalyticsReadStore mongoAnalyticsReadStore(MongoTemplate mongoTemplate) {
        return new MongoAnalyticsReadStore(mongoTemplate);
    }
}
