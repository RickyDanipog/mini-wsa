package com.akamai.wsa.analytics.infrastructure.config;

import com.akamai.wsa.analytics.domain.port.AnalyticsReadStore;
import com.akamai.wsa.analytics.domain.query.StatisticsQuery;
import com.akamai.wsa.analytics.domain.query.TimeRange;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
class ReadStoreConfigurationTest {

    @Autowired
    AnalyticsReadStore readStore;

    @Test
    void defaultProfileProvidesSeededInMemoryReadStore() {
        assertThat(readStore).isNotNull();
        assertThat(readStore.summarize(new StatisticsQuery(null, TimeRange.unbounded())).totalEvents())
                .isGreaterThan(0);
    }
}
