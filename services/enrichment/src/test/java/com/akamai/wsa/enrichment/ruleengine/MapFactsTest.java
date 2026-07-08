package com.akamai.wsa.enrichment.ruleengine;

import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class MapFactsTest {

    private MapFacts sampleFacts() {
        Map<String, Object> rule = new HashMap<>();
        rule.put("severity", "CRITICAL");
        Map<String, Object> geoLocation = new HashMap<>();
        geoLocation.put("country", "CN");
        Map<String, Object> values = new HashMap<>();
        values.put("rule", rule);
        values.put("geoLocation", geoLocation);
        values.put("action", "DENY");
        values.put("statusCode", 403);
        return new MapFacts(values);
    }

    @Test
    void resolvesNestedPath() {
        assertThat(sampleFacts().valueOf("rule.severity")).isEqualTo("CRITICAL");
        assertThat(sampleFacts().valueOf("geoLocation.country")).isEqualTo("CN");
    }

    @Test
    void resolvesTopLevelKey() {
        assertThat(sampleFacts().valueOf("action")).isEqualTo("DENY");
        assertThat(sampleFacts().valueOf("statusCode")).isEqualTo(403);
    }

    @Test
    void returnsNullForAbsentKey() {
        assertThat(sampleFacts().valueOf("missing")).isNull();
        assertThat(sampleFacts().valueOf("rule.missing")).isNull();
        assertThat(sampleFacts().valueOf("geoLocation.city")).isNull();
    }

    @Test
    void returnsNullWhenIntermediateIsNotAMap() {
        assertThat(sampleFacts().valueOf("action.name")).isNull();
        assertThat(sampleFacts().valueOf("rule.severity.length")).isNull();
    }
}
