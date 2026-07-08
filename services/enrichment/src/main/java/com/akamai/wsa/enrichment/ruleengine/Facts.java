package com.akamai.wsa.enrichment.ruleengine;

@FunctionalInterface
public interface Facts {
    Object valueOf(String key);
}
