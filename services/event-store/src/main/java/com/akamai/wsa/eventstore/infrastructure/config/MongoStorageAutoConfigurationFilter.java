package com.akamai.wsa.eventstore.infrastructure.config;

import org.springframework.boot.autoconfigure.AutoConfigurationImportFilter;
import org.springframework.boot.autoconfigure.AutoConfigurationMetadata;
import org.springframework.context.EnvironmentAware;
import org.springframework.core.env.Environment;

import java.util.Set;

public class MongoStorageAutoConfigurationFilter implements AutoConfigurationImportFilter, EnvironmentAware {

    private static final String STORAGE_PROPERTY = "wsa.storage";
    private static final String MONGO_STORAGE = "mongo";
    private static final Set<String> MONGO_AUTO_CONFIGURATIONS = Set.of(
            "org.springframework.boot.autoconfigure.mongo.MongoAutoConfiguration",
            "org.springframework.boot.autoconfigure.data.mongo.MongoDataAutoConfiguration",
            "org.springframework.boot.autoconfigure.data.mongo.MongoRepositoriesAutoConfiguration");

    private Environment environment;

    @Override
    public void setEnvironment(Environment environment) {
        this.environment = environment;
    }

    @Override
    public boolean[] match(String[] autoConfigurationClasses, AutoConfigurationMetadata autoConfigurationMetadata) {
        boolean mongoStorageActive = MONGO_STORAGE.equalsIgnoreCase(
                environment.getProperty(STORAGE_PROPERTY, "inmemory"));
        boolean[] matches = new boolean[autoConfigurationClasses.length];
        for (int index = 0; index < autoConfigurationClasses.length; index++) {
            String autoConfigurationClass = autoConfigurationClasses[index];
            matches[index] = mongoStorageActive
                    || autoConfigurationClass == null
                    || !MONGO_AUTO_CONFIGURATIONS.contains(autoConfigurationClass);
        }
        return matches;
    }
}
