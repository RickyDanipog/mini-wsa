package com.akamai.wsa.generator;

import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

class GeneratorPropertiesTest {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withUserConfiguration(TestConfig.class);

    @EnableConfigurationProperties(GeneratorProperties.class)
    static class TestConfig {
    }

    @Test
    void bindsPropertiesFromConfiguration() {
        contextRunner
                .withPropertyValues(
                        "wsa.generator.seed=42",
                        "wsa.generator.total-events=1000",
                        "wsa.generator.wave-count=5",
                        "wsa.generator.wave-size=50",
                        "wsa.generator.batch-size=200",
                        "wsa.generator.base-timestamp=2026-05-20T14:00:00Z",
                        "wsa.generator.target-url=http://localhost:8081",
                        "wsa.generator.output-mode=HTTP")
                .run(context -> {
                    GeneratorProperties properties = context.getBean(GeneratorProperties.class);
                    assertThat(properties.seed()).isEqualTo(42L);
                    assertThat(properties.totalEvents()).isEqualTo(1000);
                    assertThat(properties.waveCount()).isEqualTo(5);
                    assertThat(properties.baseTimestamp()).isEqualTo(Instant.parse("2026-05-20T14:00:00Z"));
                    assertThat(properties.targetUrl()).isEqualTo("http://localhost:8081");
                    assertThat(properties.outputMode()).isEqualTo(GeneratorProperties.OutputMode.HTTP);
                });
    }

    @Test
    void appliesDefaultsWhenUnset() {
        contextRunner.run(context -> {
            GeneratorProperties properties = context.getBean(GeneratorProperties.class);
            assertThat(properties.totalEvents()).isEqualTo(10000);
            assertThat(properties.outputMode()).isEqualTo(GeneratorProperties.OutputMode.STDOUT);
        });
    }
}
