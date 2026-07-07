package com.akamai.wsa.analytics.infrastructure.config;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.env.EnvironmentPostProcessor;
import org.springframework.core.Ordered;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.MapPropertySource;

import java.util.HashMap;
import java.util.Map;

public class MongoStorageEnvironmentPostProcessor implements EnvironmentPostProcessor, Ordered {

    private static final String PROPERTY_SOURCE_NAME = "wsaMongoAutoConfigToggle";
    private static final String AUTOCONFIGURE_EXCLUDE = "spring.autoconfigure.exclude";
    private static final String STORAGE_PROPERTY = "wsa.storage";
    private static final String MONGO = "mongo";

    @Override
    public void postProcessEnvironment(ConfigurableEnvironment environment, SpringApplication application) {
        if (MONGO.equals(environment.getProperty(STORAGE_PROPERTY))) {
            Map<String, Object> overrides = new HashMap<>();
            overrides.put(AUTOCONFIGURE_EXCLUDE, "");
            environment.getPropertySources().addFirst(new MapPropertySource(PROPERTY_SOURCE_NAME, overrides));
        }
    }

    @Override
    public int getOrder() {
        return Ordered.LOWEST_PRECEDENCE;
    }
}
