package com.akamai.wsa.analytics.infrastructure.config;

import com.akamai.wsa.analytics.domain.port.AnalyticsReadStore;
import com.akamai.wsa.analytics.domain.query.StatisticsQuery;
import com.akamai.wsa.analytics.domain.query.TimeRange;
import com.akamai.wsa.analytics.infrastructure.persistence.inmemory.InMemoryAnalyticsReadStore;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
class ReadStoreConfigurationTest {

    @Autowired
    AnalyticsReadStore readStore;

    @Autowired
    ApplicationContext applicationContext;

    @Test
    void defaultProfileProvidesSeededInMemoryReadStore() {
        assertThat(readStore).isInstanceOf(InMemoryAnalyticsReadStore.class);
        assertThat(readStore.summarize(new StatisticsQuery(null, TimeRange.unbounded())).totalEvents())
                .isGreaterThan(0);
    }

    @Test
    void defaultProfileWiresNoMongoInfrastructure() {
        assertThat(applicationContext.getBeanNamesForType(
                org.springframework.data.mongodb.core.MongoTemplate.class)).isEmpty();
        assertThat(applicationContext.getBeanNamesForType(com.mongodb.client.MongoClient.class)).isEmpty();
    }
}
