package com.akamai.wsa.enrichment.infrastructure.rules;

import com.akamai.wsa.contracts.Action;
import com.akamai.wsa.contracts.AttackCategory;
import com.akamai.wsa.contracts.GeoLocationMessage;
import com.akamai.wsa.contracts.RawEventMessage;
import com.akamai.wsa.contracts.RuleMessage;
import com.akamai.wsa.contracts.Severity;
import com.akamai.wsa.enrichment.domain.port.FactsFactory;
import com.akamai.wsa.enrichment.domain.service.FactKey;
import com.akamai.wsa.enrichment.ruleengine.Facts;
import com.akamai.wsa.enrichment.ruleengine.MapFacts;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

@Component
public class JacksonFactsFactory implements FactsFactory {

    private static final TypeReference<Map<String, Object>> MAP_TYPE = new TypeReference<>() {
    };

    private static final RawEventMessage SAMPLE_EVENT = new RawEventMessage(
            "sample", Instant.EPOCH, 0, "sample", "sample", "sample", "sample", "GET", 0, "sample",
            new RuleMessage("sample", "sample", "sample", Severity.LOW, AttackCategory.INJECTION),
            Action.MONITOR,
            new GeoLocationMessage("sample", "sample"),
            0L, 0L);

    private final ObjectMapper objectMapper;

    public JacksonFactsFactory(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    @Override
    public Facts create(RawEventMessage event, long offenderEventCount) {
        Map<String, Object> values = new HashMap<>(objectMapper.convertValue(event, MAP_TYPE));
        values.put(FactKey.OFFENDER_EVENT_COUNT, offenderEventCount);
        return new MapFacts(values);
    }

    @Override
    public Set<String> availableFactKeys() {
        Set<String> keys = new LinkedHashSet<>();
        flatten(objectMapper.convertValue(SAMPLE_EVENT, MAP_TYPE), "", keys);
        keys.add(FactKey.OFFENDER_EVENT_COUNT);
        return keys;
    }

    private static void flatten(Map<?, ?> map, String prefix, Set<String> keys) {
        for (Map.Entry<?, ?> entry : map.entrySet()) {
            String path = prefix.isEmpty() ? String.valueOf(entry.getKey()) : prefix + "." + entry.getKey();
            if (entry.getValue() instanceof Map<?, ?> nested) {
                flatten(nested, path, keys);
            } else {
                keys.add(path);
            }
        }
    }
}
