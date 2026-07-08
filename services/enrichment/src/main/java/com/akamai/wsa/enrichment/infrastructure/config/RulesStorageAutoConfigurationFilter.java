package com.akamai.wsa.enrichment.infrastructure.config;

import org.springframework.boot.autoconfigure.AutoConfigurationImportFilter;
import org.springframework.boot.autoconfigure.AutoConfigurationMetadata;
import org.springframework.context.EnvironmentAware;
import org.springframework.core.env.Environment;

import java.util.Set;

public class RulesStorageAutoConfigurationFilter implements AutoConfigurationImportFilter, EnvironmentAware {

    private static final String RULES_PROPERTY = "wsa.rules";
    private static final String POSTGRES_RULES = "postgres";
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
        boolean postgresRulesActive = POSTGRES_RULES.equalsIgnoreCase(
                environment.getProperty(RULES_PROPERTY, "inmemory"));
        boolean[] matches = new boolean[autoConfigurationClasses.length];
        for (int index = 0; index < autoConfigurationClasses.length; index++) {
            String autoConfigurationClass = autoConfigurationClasses[index];
            matches[index] = postgresRulesActive
                    || autoConfigurationClass == null
                    || !POSTGRES_AUTO_CONFIGURATIONS.contains(autoConfigurationClass);
        }
        return matches;
    }
}
