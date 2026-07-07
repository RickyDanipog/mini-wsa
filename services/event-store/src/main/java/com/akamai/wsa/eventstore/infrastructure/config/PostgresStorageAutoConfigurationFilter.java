package com.akamai.wsa.eventstore.infrastructure.config;

import org.springframework.boot.autoconfigure.AutoConfigurationImportFilter;
import org.springframework.boot.autoconfigure.AutoConfigurationMetadata;
import org.springframework.context.EnvironmentAware;
import org.springframework.core.env.Environment;

import java.util.Set;

public class PostgresStorageAutoConfigurationFilter implements AutoConfigurationImportFilter, EnvironmentAware {

    private static final String STORAGE_PROPERTY = "wsa.storage";
    private static final String POSTGRES_STORAGE = "postgres";
    private static final Set<String> POSTGRES_AUTO_CONFIGURATIONS = Set.of(
            "org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration",
            "org.springframework.boot.autoconfigure.jdbc.DataSourceTransactionManagerAutoConfiguration",
            "org.springframework.boot.autoconfigure.jdbc.JdbcTemplateAutoConfiguration",
            "org.springframework.boot.autoconfigure.jdbc.JdbcClientAutoConfiguration");

    private Environment environment;

    @Override
    public void setEnvironment(Environment environment) {
        this.environment = environment;
    }

    @Override
    public boolean[] match(String[] autoConfigurationClasses, AutoConfigurationMetadata autoConfigurationMetadata) {
        boolean postgresStorageActive = POSTGRES_STORAGE.equalsIgnoreCase(
                environment.getProperty(STORAGE_PROPERTY, "inmemory"));
        boolean[] matches = new boolean[autoConfigurationClasses.length];
        for (int index = 0; index < autoConfigurationClasses.length; index++) {
            String autoConfigurationClass = autoConfigurationClasses[index];
            matches[index] = postgresStorageActive
                    || autoConfigurationClass == null
                    || !POSTGRES_AUTO_CONFIGURATIONS.contains(autoConfigurationClass);
        }
        return matches;
    }
}
